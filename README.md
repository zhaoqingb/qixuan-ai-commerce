# 栖选商城 · AI 电商

Spring Boot 4 + Vue 3 的前后分离电商项目，带一个**能真正调用业务工具、写操作走人工审批**的 AI Agent。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 4 · MyBatis-Plus · MySQL 8.4 · Flyway |
| 中间件 | Redis 7（缓存/版本/取消标志） · Kafka 3.9（秒杀订单异步落库 + 死信队列） |
| AI | DashScope OpenAI 兼容接口 · Function Calling · ReAct 循环 |
| 前端 | Vue 3 · Vite · TypeScript · Element Plus（买家端 + 商家端） |
| 认证 | JWT · RBAC 角色权限 |

## 一键起环境

```bash
cp .env.example .env          # 填数据库密码
docker compose up -d          # MySQL + Redis + Kafka
docker compose ps             # 确认三个服务 healthy
```

然后分别启动后端和前端（应用本身在宿主机跑，方便调试）：

```bash
# 后端
cd services/commerce-api
cp src/main/resources/application.properties.example src/main/resources/application.properties
# 按本地情况改密码；AI 功能需要设置环境变量 DASHSCOPE_API_KEY
./mvnw spring-boot:run

# 买家端
cd apps/mall-web && npm install && npm run dev      # http://localhost:5174

# 商家端
cd apps/merchant-web && npm install && npm run dev  # http://localhost:5173
```

## 核心模块

- **交易链路**：购物车 → 结算预览 → 下单（事务 + 行锁）→ 支付状态机 → 发货 → 收货 → 退款售后
- **秒杀**：Redis 库存预扣 → Kafka 异步落库 → 消费失败投死信队列 → 超卖防护
- **权限**：JWT 认证 · 管理员/商家/买家三级角色 · 运维接口独立前缀
- **AI Agent**：8 个业务工具按角色下发 · 写操作生成审批单（参数固化 + 10 分钟过期）· 工具调用审计 · token 级 SSE 流式输出 · 轮次中断与失败熔断
- **分页**：MyBatis-Plus 页码分页 + 游标分页（深分页优化）+ 聚合查询手写 count

## 测试

```bash
cd services/commerce-api && ./mvnw test
```

覆盖参数解析边界、JSON 转义、库存校验、缓存键生成、审批描述降级等纯逻辑。

## 目录结构

```
apps/
  mall-web/          买家端（商品、购物车、下单、订单、退款）
  merchant-web/      商家端（商品管理、订单、秒杀、AI 助手）
services/commerce-api/
  src/main/java/com/qixuan/commerce/
    controller/      16 个 HTTP 接口层
    service/         27 个业务服务
    mapper/          22 个 MyBatis 映射
    dto/ entity/     数据传输对象与实体
    common/          Agent 工具、LLM 客户端、拦截器
  src/main/resources/db/migration/   Flyway 迁移脚本
```
