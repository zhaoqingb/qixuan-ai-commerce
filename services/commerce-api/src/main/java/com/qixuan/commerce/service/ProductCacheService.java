package com.qixuan.commerce.service;

import com.qixuan.commerce.dto.ProductCardView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 商品列表缓存。
 *
 * 三个设计取舍：
 * 1. 只缓存「浏览」请求（全量 / 按分类），不缓存搜索 —— 关键词是长尾，key 无限且命中率低。
 * 2. 失效用「版本号前缀 + INCR」，不用 KEYS。KEYS 在大 key 量下会阻塞整个 Redis。
 * 3. Redis 任何异常都降级成「直接查库」—— 缓存是加速器，不是依赖。
 */
@Service
public class ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheService.class);

    private static final String KEY_PREFIX = "mall:products:";
    private static final String VERSION_KEY = "mall:products:version";

    /** 正常数据缓存 60 秒：价格/标题变更不频繁，这个窗口可接受 */
    private static final Duration TTL = Duration.ofSeconds(60);

    /** 空结果只缓存 30 秒：防穿透，但也不能让「刚上架」等太久 */
    private static final Duration EMPTY_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public ProductCacheService(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Cache-Aside：先查缓存 → 未命中查库 → 写回缓存。
     *
     * @param categoryId null 表示查全部
     * @param loader     真正的数据库查询
     */
    public List<ProductCardView> getOrLoad(Long categoryId, Supplier<List<ProductCardView>> loader) {

        // 注意 try 的范围：buildKey() 内部也要访问 Redis（读版本号），
        // 只包 get() 是不够的 —— Redis 挂掉时会在 buildKey 里抛出去。
        String key;
        try {
            key = buildKey(categoryId);
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                ProductCardView[] array = jsonMapper.readValue(cached, ProductCardView[].class);
                return Arrays.asList(array);
            }
        } catch (Exception ex) {
            // 读缓存这条链全挂了：直接回源。这里调 loader 是安全的，
            // 因为还没执行过它，不会重复查库。
            log.warn("商品缓存不可用，降级查库", ex);
            return loader.get();
        }

        // 缓存链路正常，但没命中 —— 查库。
        // 故意放在 try 外面：数据库异常不该被当成"缓存异常"吞掉。
        List<ProductCardView> fresh = loader.get();

        try {
            if (fresh.isEmpty()) {
                redis.opsForValue().set(key, "[]", EMPTY_TTL);
            } else {
                redis.opsForValue().set(key, jsonMapper.writeValueAsString(fresh), jitter(TTL));
            }
        } catch (Exception ex) {
            log.warn("写商品缓存失败，忽略", ex);
        }

        return fresh;
    }

    /**
     * 商品发生任何变更时调用（新建 / 改价 / 改标题 / 上下架）。
     *
     * 只递增版本号，不删除任何 key —— 旧 key 会自动被新前缀"顶掉"，
     * 然后靠 TTL 自然过期。这样既避免 KEYS 阻塞，也不用遍历删除。
     */
    public void evictAll() {
        try {
            redis.opsForValue().increment(VERSION_KEY);
            log.info("商品缓存版本号已递增，旧缓存自然失效");
        } catch (Exception ex) {
            // 清缓存失败不影响业务：最坏情况是 60 秒后自然过期
            log.warn("递增缓存版本号失败", ex);
        }
    }

    /** 当前版本号；不存在时初始化为 1 */
    private String currentVersion() {
        String version = redis.opsForValue().get(VERSION_KEY);
        if (version == null) {
            redis.opsForValue().setIfAbsent(VERSION_KEY, "1");
            version = redis.opsForValue().get(VERSION_KEY);
        }
        return version == null ? "1" : version;
    }

    /** key 里带版本号，版本一变，整批旧 key 立刻不再被读取 */
    private String buildKey(Long categoryId) {
        String category = categoryId == null ? "all" : String.valueOf(categoryId);
        return KEY_PREFIX + "v" + currentVersion() + ":" + category;
    }

    /** TTL 加随机抖动，避免大批 key 同时过期把数据库打穿（缓存雪崩） */
    private Duration jitter(Duration base) {
        return base.plusSeconds(ThreadLocalRandom.current().nextInt(0, 10));
    }
}
