# 03 · Redis 与缓存

> 一句话定位：**Redis 是闸门，不是账本。** DB 的 `seckill_stock` 才是最终真相。

## 坑 1：为什么必须用 Lua

```java
// ❌ 三条命令，中间会被别的客户端插进来
Long stock = redis.opsForValue().get(key);
if (stock > 0) {
    redis.opsForValue().decrement(key);
}
```

Redis 是单线程的，但**单条命令之间**会插入其他客户端的命令。
1000 件库存能被 3000 个请求同时判断为"还有"。

```lua
local stock = redis.call('GET', KEYS[1])
if not stock then return -1 end
if redis.call('EXISTS', KEYS[2]) == 1 then return 1 end
if tonumber(stock) <= 0 then return 0 end
redis.call('DECR', KEYS[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])
return 2
```

**Redis 执行 Lua 期间不会插入任何其他命令**，判断和扣减才有原子性。

### 但 Lua 不是事务：它没有回滚

已经执行的 `redis.call` 写操作，**不会因为后面 `return` 而撤销**。
所以脚本必须保证"写完就一定能正确返回"，不能指望出错回滚。

### 判断顺序也有讲究

先查"这个人买过没"，再判断"库存够不够"：

```
库存刚卖完，一个早就买过的用户再点一次
→ 先查 EXISTS 才知道该说"你已购买"
→ 先查库存只会说"已售罄"，对他没用
```

---

## 坑 2：`script.execute(redis)` 调反了（犯过 3 次）

```java
// ❌ DefaultRedisScript 只是数据容器，没有任何执行能力
deductScript.execute(redis, keys, args);

// ✅ Template 是执行者，Script 是参数
redis.execute(deductScript, keys, args);
```

**记住分工**：`DefaultRedisScript` 装的是"要执行的命令文本 + 返回类型"，
真正能跟 Redis 通信的是 `StringRedisTemplate`。

---

## 坑 3：参数必须传 String

```java
// ❌ ClassCastException
redis.execute(script, keys, USER_KEY_TTL_SECONDS);   // long

// ✅
redis.execute(script, keys, String.valueOf(USER_KEY_TTL_SECONDS));
```

`StringRedisTemplate` 的值序列化器是 `RedisSerializer.string()`，
内部有个 `Object → String` 的强转，传 `long` 直接炸。

### 更隐蔽的：`String.valueOf(Duration)`

```java
private static final Duration STOCK_TTL = Duration.ofHours(24);

String.valueOf(STOCK_TTL)         // ❌ "PT24H"
STOCK_TTL.toSeconds()             // ✅ 86400
```

`Duration.toString()` 返回 ISO-8601 格式的 `"PT24H"`。
传给 `SET ... EX ARGV[2]`，Redis 报错：`ERR invalid expire time in 'set' command`。

---

## 坑 4：预热必须用 `setIfAbsent`，不能用 `set`

```java
// ❌ 会覆盖已经扣减过的库存
redis.opsForValue().set(key, String.valueOf(stock));

// ✅ 只有一个线程能写进去
redis.opsForValue().setIfAbsent(key, String.valueOf(stock), TTL);
```

### 覆盖的后果

```
并发场景：A 灌 1000，扣到 995；B 再灌 1000，把 995 覆盖掉
结果：DB 卖了 5 件，Redis 认为还有 1000 → 直接超卖
```

### 顺带：必须带 TTL

不带过期的 key 会永久存在。下一场活动复用同一个 itemId 时，
预热会被 `setIfAbsent` 判成"已存在"而跳过，库存卡在上一场的残值上。

---

## 坑 5：`INCR` 一个不存在的 key 会造出幽灵库存

```java
// ❌ 如果 key 已经被清理，INCR 会凭空创建 stock = 1
redis.opsForValue().increment(stockKey);

// ✅ 先确认 key 还在
if (Boolean.TRUE.equals(redis.hasKey(stockKey))) {
    redis.opsForValue().increment(stockKey);
}
```

---

## 坑 6：回补必须撤"全部痕迹"

Lua 里写了两次写操作（`DECR` 库存 + `SET` 一人一单标记），
rollback 就必须撤两次。

```java
public void rollback(Long itemId, Long userId) {
    try {
        if (Boolean.TRUE.equals(redis.hasKey(stockKey(itemId)))) {
            redis.opsForValue().increment(stockKey(itemId));   // 还库存
        }
        redis.delete(userKey(itemId, userId));                 // 撤销购买资格
    } catch (Exception ex) {
        // 绝不能往外抛：上层正在处理 DB 异常，这里再抛会把真正的错误盖掉
        log.error("秒杀名额回补失败，需要人工对账 itemId={}, userId={}", itemId, userId, ex);
    }
}
```

### 只还库存的后果

DB 落库失败 → 回补只加了库存、标记还在 →
这个用户重试时 Lua 的 `EXISTS` 返回 1 → 判成 `DUPLICATED` →
**他永远买不到了**（得等 24 小时 TTL 过期）。

**判据：Lua 里有几次写操作，rollback 就得撤几次。**

---

## 坑 7：缓存值不能用于写操作

### 场景

`buy()` 里加了本地缓存（item + activity，TTL 5 秒）。
库存没预热时要懒加载：

```java
// ❌ item 来自缓存，可能是 5 秒前的快照
if (result == NOT_READY) {
    stockService.loadStock(itemId, item.getSeckillStock());
}
```

如果这 5 秒里卖掉了 50 件，缓存里还是旧值，用它灌 Redis
等于**凭空放出 50 个名额 → 超卖**。

```java
// ✅ 写操作前回源
SeckillItem latest = itemMapper.selectById(itemId);
stockService.loadStock(itemId, latest.getSeckillStock());
```

### 通用原则

**读可以用缓存，写必须回源。**
凡是拿缓存值去计算的写操作，都要停下来想一遍"这个值过期了会怎样"。

---

## 坑 8：Cache-Aside 的三个细节

### ① try 的范围必须包住 buildKey

```java
// ❌ buildKey() 内部也要访问 Redis（读版本号），只包 get() 不够
String key = buildKey(categoryId);
try {
    String cached = redis.opsForValue().get(key);
} catch (Exception ex) { ... }

// ✅
String key;
try {
    key = buildKey(categoryId);
    String cached = redis.opsForValue().get(key);
} catch (Exception ex) {
    return loader.get();   // Redis 挂了就降级查库
}
```

### ② 反序列化数组要用 `[].class`

```java
// ❌ readValue 返回单对象，转 List 会失败
List<ProductCardView> list = mapper.readValue(cached, List.class);

// ✅
ProductCardView[] array = jsonMapper.readValue(cached, ProductCardView[].class);
return Arrays.asList(array);
```

### ③ 失效用版本号 + `INCR`，不要用 `KEYS`

```java
public void evictAll() {
    redis.opsForValue().increment(VERSION_KEY);   // 版本号一变，整批旧 key 立刻失效
}
```

`KEYS pattern` 会**阻塞整个 Redis**（单线程逐一遍历）。
版本号方案让旧 key 自然过期，零阻塞。

### ④ TTL 加随机抖动

```java
return base.plusSeconds(ThreadLocalRandom.current().nextInt(0, 10));
```

避免大批 key 同时过期把数据库打穿（缓存雪崩）。

---

## 坑 9：对账必须单向 —— 只往下修，不往上修

### 为什么需要

Redis 会丢数据：重启没持久化、主从切换丢最近写入、内存满被 LRU 淘汰、误删。
一旦 Redis 的库存**多于** DB，就会超卖。

### 反直觉的地方

看到"不一致就按 DB 修正"是错的：

```
DB    seckill_stock = 500      （队列里 100 单还没落库）
Redis seckill_stock = 400      （已经扣了 100 个）
```

看起来"不一致"，但这**完全正常**——那 100 单正在队列里等着刷盘。

如果按 DB 重置成 500：

```
Redis 变 500 → 又卖 500 个 → 队列里 100 单随后落库
结果：卖了 600，DB 库存被扣成负 → 超卖
```

### 正确规则

| 情况 | 判断 | 动作 |
|---|---|---|
| `Redis > DB` | Redis 凭空多出来了，**超卖风险** | 修正成 DB 的值 |
| `Redis < DB` | 队列里有未落库订单，**正常中间状态** | 不动，只记 debug 日志 |
| `Redis == null` | key 丢了（Redis 重启过） | 用 DB 的值重建 |

**对账只"往下修"，不"往上修"。**

### 定时任务

```java
@Scheduled(fixedDelay = 30_000L, initialDelay = 10_000L)
public void reconcile() { ... }
```

30 秒一轮。太频繁浪费，太稀疏则漂移窗口太长。

### 验证方法（两个方向都要测）

```bash
# 危险方向：应被压下
docker exec qixuan-redis redis-cli SET seckill:stock:1 999
# 等 35 秒 → 回到 DB 的值，日志有 error

# 安全方向：不能动
docker exec qixuan-redis redis-cli SET seckill:stock:1 100
# 等 35 秒 → 还是 100
```

**第二个方向是关键验收点。如果它被"修正"回 500，线上就会超卖。**

---

## 坑 10：状态码语义

| 码 | 用在哪 |
|---|---|
| 400 | 参数格式错（`@Valid` 拦） |
| 404 | 资源不存在，**包括"存在但不属于你"** |
| 409 | 状态冲突（已售罄、已购买、状态已变） |
| 503 | 系统过载（队列满），不是业务失败 |

队列满时用 503 而不是 409：
409 意味着"你这次不满足条件，算了"，503 意味着"系统暂时扛不住，待会儿再来"。
