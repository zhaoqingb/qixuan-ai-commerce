package com.qixuan.commerce.common;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * 应用内令牌桶限流（Redis + Lua 原子实现）。
 *
 * 定位要说清楚：这是「请求已经进了 Tomcat 之后」的限流，
 * 保护的是业务方法和数据库连接池，保护不了 Tomcat 自己的线程池。
 * 真正的网关限流（Nginx / Kong）要挡在应用前面，本项目没做。
 *
 * 为什么用 Lua：补令牌 + 判断 + 扣减是三步，拆成多条 Redis 命令会有竞态，
 * 高并发下可能一起通过。Lua 在 Redis 里串行执行，三步才有原子性。
 */
@Component
public class RateLimiter {

    /**
     * KEYS[1] = 桶的 key
     * ARGV[1] = 当前时间戳（毫秒）
     * ARGV[2] = 桶容量
     * ARGV[3] = 每秒补充速率
     *
     * 返回 1 = 拿到令牌放行；0 = 没有令牌拒绝。
     */
    private static final String LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local rate = tonumber(ARGV[3])

            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])

            if tokens == nil or ts == nil then
              tokens = capacity
              ts = now
            end

            -- 按经过时间补令牌，但不超过桶容量
            local elapsed = math.max(0, now - ts)
            tokens = math.min(capacity, tokens + elapsed * rate / 1000)

            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end

            redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
            -- 5 分钟没人访问就自动清理，避免无限累积 key
            redis.call('EXPIRE', key, 300)

            return allowed
            """;

    /** 商品列表：防刷接口。压测用这个桶观察限流效果 */
    public static final String BUCKET_PRODUCT_LIST = "ratelimit:product:list";
    /** 秒杀下单：最容易被脚本刷的接口 */
    public static final String BUCKET_SECKILL_BUY = "ratelimit:seckill:buy";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public RateLimiter(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    /** 尝试取一个令牌。capacity=桶容量，rate=每秒补充个数 */
    public boolean tryAcquire(String bucket, int capacity, int rate) {
        Long result = redis.execute(script, List.of(bucket),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(capacity), String.valueOf(rate));
        // Redis 挂了要放行：限流组件不能把整个应用拖垮（降级优先于精确）
        if (result == null) {
            return true;
        }
        return result == 1L;
    }
}
