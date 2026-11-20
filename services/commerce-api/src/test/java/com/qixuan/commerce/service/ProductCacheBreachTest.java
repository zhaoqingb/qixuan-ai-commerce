package com.qixuan.commerce.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 缓存击穿防护的确定性测试。
 *
 * 注：真正验证"20 个并发只有一个回源"用 Mock 模拟不了时序（Mock 的返回值序列
 * 在所有线程间共享，先到的线程直接读到缓存）。那部分靠真实压测验证。
 * 这里守住两个逻辑确定的分支：抢到锁时回源、抢不到锁但缓存有值时不回源。
 */
class ProductCacheBreachTest {

    @Test
    void 抢到锁时回源并写缓存() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("mall:products:version")).thenReturn("1");
        when(valueOps.get("mall:products:v1:all")).thenReturn(null);
        when(valueOps.setIfAbsent(any(String.class), any(String.class), any(java.time.Duration.class)))
                .thenReturn(true);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(List.class), any(Object[].class))).thenReturn(1L);

        ProductCacheService service = new ProductCacheService(redis, JsonMapper.builder().build());
        var loaderCalls = new java.util.concurrent.atomic.AtomicInteger();

        var result = service.getOrLoad(null, () -> {
            loaderCalls.incrementAndGet();
            return List.of();
        });

        assertEquals(1, loaderCalls.get(), "抢到锁的请求应该回源查库一次");
        assertTrue(result.isEmpty());
    }

    @Test
    void 抢不到锁时重试读缓存() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        var valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("mall:products:version")).thenReturn("1");
        // 锁一直抢不到，但缓存有值 —— 应该走"读缓存"路径，不查库
        when(valueOps.setIfAbsent(any(String.class), any(String.class), any(java.time.Duration.class)))
                .thenReturn(false);
        when(valueOps.get("mall:products:v1:all")).thenReturn("[]");

        ProductCacheService service = new ProductCacheService(redis, JsonMapper.builder().build());
        var loaderCalls = new java.util.concurrent.atomic.AtomicInteger();

        var result = service.getOrLoad(null, () -> {
            loaderCalls.incrementAndGet();
            return List.of();
        });

        assertEquals(0, loaderCalls.get(), "缓存已有值时，抢不到锁也不该查库");
        assertTrue(result.isEmpty());
    }
}
