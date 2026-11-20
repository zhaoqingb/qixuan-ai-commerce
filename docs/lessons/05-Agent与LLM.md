# 05 · Agent 与 LLM

## 一、Agent Loop 的核心：消息必须原样回填

### 错误做法

拿到模型返回的 `tool_calls` 后，自己拼一条"工具调用结果"消息塞回去。
模型下一轮会认不出这是哪个调用的结果。

### 正确做法

```java
// 1. 把模型返回的原始 message 原样加入历史（含 tool_calls 结构）
history.add(rawAssistantMessage);

// 2. 每个工具结果包成一条独立消息，用 tool_call_id 关联
for (ToolCall call : rawAssistantMessage.toolCalls()) {
    String result = execute(call);
    history.add(Map.of(
        "role", "tool",
        "tool_call_id", call.id(),        // ← 关键
        "content", result
    ));
}
```

### 踩过的坑

```java
// ❌ 字段名写成 name，模型当普通消息处理，不知道自己调用过工具
Map.of("role", "tool", "name", call.name(), "content", result)

// ✅
Map.of("role", "tool", "tool_call_id", call.id(), "content", result)
```

### 还有一个 NPE

```java
// ❌ Map.of 不接受 null 值，直接 NPE
Map.of("role", "tool", "tool_call_id", id, "content", null);

// ✅ 空结果也要给个字符串
Map.of("role", "tool", "tool_call_id", id, "content", "");
```

---

## 二、工具按角色下发，防止模型越权

```java
public List<ToolDefinition> definitions(Long userId) {
    List<ToolDefinition> tools = new ArrayList<>();
    if (isBuyer(userId)) {
        tools.add(QUERY_MY_ORDERS);
        tools.add(QUERY_PRODUCT);
    }
    if (isMerchant(userId)) {
        tools.add(QUERY_STORE_ORDERS);
        tools.add(SHIP_ORDER);          // 商家专属
    }
    return tools;
}
```

**不给模型看到它没权限的工具**，比"给了再在服务端拒绝"可靠得多。
模型不会去调用一个它不知道存在的函数。

---

## 三、模型幻觉：最危险的一次

### 现象

商家发货工具，参数是 `storeOrderId`。测试时商家说"发一下 25 号订单"，
模型**自信地传了 `storeOrderId = 1`**。

服务端校验：订单存在 ✓、属于该店铺 ✓、状态是 PAID ✓ —— **全部通过，
真的把另一个用户的 1 号订单发了货**。

### 根因

`storeOrderId` 是一个自增数字，对模型来说"1"和"25"没有语义差别。
模型在自然语言到 id 的映射上完全不可靠。

### 修法：把参数换成业务单号

```java
// ❌ 自增主键，模型容易猜错
ship_order(storeOrderId: 1)

// ✅ 业务单号，不可枚举、有前缀、人也能核对
ship_order(subOrderNo: "SO202611180001")
```

**原则：暴露给模型的参数，必须是"人类口语里也会说的那个标识符"。**
用户说"25 号订单"时，如果单号本身长得像 `SO...0001`，模型就必须先查一次，
查完再传，反而不容易错。

### 补充防护

1. 工具描述里写清楚"必须先调用 query_store_orders 拿到 subOrderNo"
2. 服务端仍然校验归属和状态（纵深防御，不能只靠提示词）
3. 高风险操作走人在环审批

---

## 四、人在环审批：参数固化 + CAS + 过期

### 为什么需要

Agent 能读数据库、能改订单状态。写操作一旦被误触发，后果不可逆。
所以凡是"改状态"的工具，都先落一条审批记录，等人点确认。

### 三个关键设计

**① 参数在申请时固化**

审批记录里存的是**完整的工具调用参数**（JSON 快照），不是"用户说了什么"。
审批通过后执行的是那份快照，避免"审批完参数被改了"。

**② 用 CAS 防止重复审批**

```sql
UPDATE ai_action_approval
SET status = 'APPROVED', decided_at = NOW()
WHERE id = ? AND status = 'PENDING'
```

`rows != 1` 说明已经被别人处理过了 → 409。

**③ 过期机制**

```java
// 审批单 30 分钟不处理就自动过期
if (approval.getExpiresAt().isBefore(LocalDateTime.now())) { ... }
```

过期后执行要拒绝，否则一个三天前的审批突然生效，用户完全不知道发生了什么。

### 踩到的坑：过期标记被回滚

`markExpired()` 把状态改成 `EXPIRED` 然后抛异常，期望"标记保留、主流程失败"。
结果标记跟着事务一起回滚了。

**修法**：`@Transactional(propagation = REQUIRES_NEW)`，且必须跨 Bean
（自调用绕过代理，见 `02-并发与事务.md` 坑 3）。

---

## 五、审计旁路

所有工具调用都记一条 `ai_tool_audit`：谁调的、调的什么、参数、返回值、耗时。

**注意：审计写入失败不能影响主流程。**

```java
try {
    auditMapper.insert(record);
} catch (Exception ex) {
    log.warn("审计写入失败", ex);   // 只记日志，不抛
}
```

审计是旁路，不是业务链路的一部分。

---

## 六、Agent 相关表设计（已存在但大多闲置）

| 表 | 用途 |
|---|---|
| `ai_conversation` / `ai_message` | 会话与消息 |
| `ai_knowledge_document` | RAG 知识库，含 `vector_document_id` |
| `ai_action_approval` | 人在环审批，含 status / expires_at / decided_at / executed_at |
| `ai_tool_audit` | 工具调用审计 |
| `outbox_event` | 事务性事件 Outbox，含 retry_count / next_retry_at |

设计是生产级骨架，不是玩具。后续要补的是：
- RAG（知识库检索增强）
- Agent Memory（跨会话记忆）
- Outbox 的实际投递逻辑

---

## 面试怎么说

> Agent 层最容易被低估的是"工具参数设计"。
> 我一开始用自增主键当参数，模型把 25 号订单传成了 1 号，
> 服务端的所有校验都通过了——因为 1 号订单确实存在、确实属于该商家、状态也对。
> 后来改成传业务单号，因为单号不可枚举，模型必须先查一次才能拿到。
> 这让我意识到：给模型的接口，参数要设计成"人类口语里也会说的那个标识符"。
>
> 另外写操作全部走人在环审批：参数在申请时固化快照，审批用 CAS 防重复，
> 超过有效期就作废。工具调用还全量记审计，但审计写入失败不影响业务。
