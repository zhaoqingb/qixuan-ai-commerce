package com.qixuan.commerce.service;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProductCacheService 的边界测试。
 *
 * buildKey 出错 → 分类切换时读到别的分类的缓存（串数据）；
 * jitter 出错 → 缓存同时过期，数据库被打穿。
 */
class ProductCacheServiceTest {

    private final StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final ProductCacheService service = new ProductCacheService(
            redis, JsonMapper.builder().build());

    private String buildKeyForVersion(Long categoryId, String version) {
        // currentVersion 依赖 Redis，这里直接测 buildKey 的分类拼接逻辑：
        // 用 mock 的 redis 返回指定版本
        org.mockito.Mockito.when(redis.opsForValue()).thenReturn(
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class));
        org.mockito.Mockito.when(redis.opsForValue().get("mall:products:version"))
                .thenReturn(version);
        return ReflectionTestUtils.invokeMethod(service, "buildKey", categoryId);
    }

    @Test
    void buildKey_分类为空() {
        assertTrue(buildKeyForVersion(null, "1").endsWith(":all"),
                "categoryId 为 null 时 key 应以 :all 结尾");
    }

    @Test
    void buildKey_带分类ID() {
        assertTrue(buildKeyForVersion(3L, "1").endsWith(":3"),
                "categoryId=3 时 key 应以 :3 结尾");
    }

    @Test
    void buildKey_带版本号() {
        assertTrue(buildKeyForVersion(null, "7").contains("v7"),
                "key 里必须带版本号，版本变更才能让旧 key 失效");
    }

    @Test
    void jitter_在范围内() {
        Duration base = Duration.ofSeconds(60);
        for (int i = 0; i < 50; i++) {
            Duration actual = ReflectionTestUtils.invokeMethod(service, "jitter", base);
            assertTrue(actual.toSeconds() >= 60 && actual.toSeconds() < 70,
                    "抖动后应在 [60, 70) 秒内，实际 " + actual.toSeconds());
        }
    }
}
