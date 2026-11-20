package com.qixuan.commerce.service;

import com.qixuan.commerce.dto.ProductCardView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
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

    /** 击穿锁最多等 3 轮，每轮 50ms —— 总计 150ms，比直接查库还快就放弃等 */
    private static final int LOCK_RETRY_TIMES = 3;
    private static final int LOCK_RETRY_INTERVAL_MS = 50;
    /** 锁的 TTL 要大于一次 DB 查询的正常耗时，但不能太长（持锁者崩了要能自动释放） */
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    /** 安全释放锁：GET 和 DEL 必须在一条 Lua 里，否则两步之间锁可能易主 */
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] "
            + "then return redis.call('del', KEYS[1]) else return 0 end";

    private final DefaultRedisScript<Long> releaseLockScript =
            new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);

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

        // 缓存未命中：可能碰上「热点 key 刚过期」的击穿场景。
        // 1000 个请求同时发现没缓存，如果都去查库，DB 会被瞬间打穿。
        // 用 SETNX 抢锁：只有一个请求查库并回写，其余短等后重试读缓存。
        return loadWithLock(key, categoryId, loader);
    }

    /**
     * 缓存击穿防护：分布式互斥锁 + 有限重试。
     *
     * 为什么不用本地 synchronized：多实例部署时每个 JVM 各锁各的，挡不住。
     * 为什么锁要带 TTL：拿到锁的线程如果崩了，锁不释放会永远没人能查库。
     */
    private List<ProductCardView> loadWithLock(String key, Long categoryId,
            Supplier<List<ProductCardView>> loader) {

        String lockKey = key + ":lock";
        String lockValue = String.valueOf(ThreadLocalRandom.current().nextLong());

        for (int attempt = 0; attempt < LOCK_RETRY_TIMES; attempt++) {
            try {
                // SETNX + 过期时间，一条命令保证原子：拿到锁的才有资格查库
                Boolean locked = redis.opsForValue().setIfAbsent(
                        lockKey, lockValue, LOCK_TTL);

                if (Boolean.TRUE.equals(locked)) {
                    try {
                        return loadAndCache(key, loader);
                    } finally {
                        // 只删自己的锁：不能直接 DEL，否则可能删掉别人后补的锁
                        releaseLock(lockKey, lockValue);
                    }
                }

                // 没抢到锁：说明别人正在查库，等它写完，再读一次缓存
                Thread.sleep(LOCK_RETRY_INTERVAL_MS);
                String cached = redis.opsForValue().get(key);
                if (cached != null) {
                    ProductCardView[] array = jsonMapper.readValue(cached, ProductCardView[].class);
                    return Arrays.asList(array);
                }
            } catch (InterruptedException ex) {
                // 线程被中断：恢复标志位，降级查库
                Thread.currentThread().interrupt();
                return loader.get();
            } catch (Exception ex) {
                // Redis 异常不该拖垮业务：降级查库
                log.warn("缓存击穿防护不可用，降级查库", ex);
                return loader.get();
            }
        }

        // 重试次数用尽（比如持锁线程崩了、锁还没到 TTL）：
        // 与其让所有请求一直等，不如直接回源 —— 宁可慢，不可全部失败
        return loader.get();
    }

    /** 查库 + 写回缓存，和原来的 Cache-Aside 逻辑一致 */
    private List<ProductCardView> loadAndCache(String key, Supplier<List<ProductCardView>> loader) {
        // 双重检查：拿到锁之后再读一次缓存，
        // 因为排队期间可能已经有前一个持锁者写好了
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            ProductCardView[] array = jsonMapper.readValue(cached, ProductCardView[].class);
            return Arrays.asList(array);
        }

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
     * 安全释放锁：比较 value 一致才删。
     * 用 Lua 而非先 GET 再 DEL —— 两步之间锁可能过期被别人抢走，
     * 那时 DEL 删的就是别人的锁了。
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            redis.execute(releaseLockScript, List.of(lockKey), lockValue);
        } catch (Exception ex) {
            // 锁没删掉也没关系：TTL 到期会自动释放
            log.warn("释放缓存锁失败，等待 TTL 自动过期", ex);
        }
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
