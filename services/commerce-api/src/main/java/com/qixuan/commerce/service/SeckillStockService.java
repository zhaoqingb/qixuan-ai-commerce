package com.qixuan.commerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 秒杀库存的 Redis 预扣（L1）。
 *
 * 定位：Redis 只是「流量闸门」，不是真相源。
 * DB 里的 seckill_stock 才是最终账本，Redis 只负责在入口挡掉绝大多数无效请求。
 *
 * 所以这里的每一个方法都是「可以失败」的：
 * Redis 出问题最多让流量回落到数据库，绝不能让整个抢购直接 500。
 */
@Service
public class SeckillStockService {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockService.class);

    /** 预扣成功，可以往下走数据库落库 */
    public static final int OK = 2;
    /** 售罄 */
    public static final int SOLD_OUT = 0;
    /** 这个用户已经买过了 */
    public static final int DUPLICATED = 1;
    /** 库存还没灌进 Redis（懒加载场景） */
    public static final int NOT_READY = -1;

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String USER_KEY_PREFIX = "seckill:user:";

    /** 一人一单标记的存活时间：活动最长 1 天，过期后自然清理，不用手动删 */
    private static final long USER_KEY_TTL_SECONDS = 86400L;

    /**
     * 库存 key 的存活时间。
     *
     * 必须带 TTL：否则活动结束、下一场活动复用同一个 itemId 时，
     * 预热会被 setIfAbsent 判成「已存在」而跳过，库存卡在上一场的残值上。
     */
    private static final Duration STOCK_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    /**
     * 预扣脚本。
     *
     * 为什么必须用 Lua：GET 判断和 DECR 扣减是两条命令，
     * 中间会被别的客户端插进来 —— 1000 件库存能被 3000 个请求同时判断为「还有」。
     * Redis 执行 Lua 期间不会插入任何其他命令，判断 + 扣减才有原子性。
     *
     * 注意 Redis 的 Lua 不是数据库事务：它没有回滚。
     * 已经执行的 redis.call 写操作不会因为后面 return 而撤销，
     * 所以脚本必须保证「写完就一定能正确返回」，不能指望出错回滚。
     *
     * 判断顺序也有讲究：先查「这个人买过没」，再判断「库存够不够」。
     * 反过来会出现这种情况 —— 库存刚卖完，一个早就买过的用户再点一次，
     * 拿到的提示是「已售罄」而不是「你已购买」。两种说法都"对"，但后者对他更有用。
     */
    private static final String DEDUCT_SCRIPT = """
            local stock = redis.call('GET', KEYS[1])
            if not stock then
                return -1
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 1
            end
            if tonumber(stock) <= 0 then
                return 0
            end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])
            return 2
            """;

    private final DefaultRedisScript<Long> deductScript;
    private final DefaultRedisScript<Long> resetScript;

    public SeckillStockService(StringRedisTemplate redis) {
        this.redis = redis;
        this.deductScript = new DefaultRedisScript<>(DEDUCT_SCRIPT, Long.class);
        this.resetScript = new DefaultRedisScript<>(RESET_SCRIPT, Long.class);
    }

    /**
     * 把数据库库存灌进 Redis（预热）。
     *
     * 用 setIfAbsent 而不是 set：并发预热时只有一个能写进去。
     * 如果两个请求都 set，后一个会把「已经扣减过的库存」覆盖回初始值 —— 直接超卖。
     *
     * @return true 表示本次真的写入了；false 表示 key 已存在、跳过
     */
    public boolean loadStock(Long itemId, int stock) {
        Boolean loaded = redis.opsForValue()
                .setIfAbsent(stockKey(itemId), String.valueOf(stock), STOCK_TTL);
        return Boolean.TRUE.equals(loaded);
    }

    /**
     * 原子预扣。
     *
     * 三个容易踩的点：
     * 1. 执行者是 StringRedisTemplate，不是 script 自己 —— script 只是「要执行的命令」。
     * 2. 脚本参数必须是 String。StringRedisTemplate 的值序列化器只认 String，
     * 直接传 long 会在内部强转时抛 ClassCastException。
     * 3. execute 返回的 Long 可能为 null（连接异常等），直接拆箱会 NPE。
     *
     * @return {@link #OK} / {@link #SOLD_OUT} / {@link #DUPLICATED} /
     *         {@link #NOT_READY}
     */
    public int tryDeduct(Long itemId, Long userId) {
        List<String> keys = List.of(stockKey(itemId), userKey(itemId, userId));
        try {
            Long raw = redis.execute(deductScript, keys, String.valueOf(USER_KEY_TTL_SECONDS));
            return raw == null ? NOT_READY : raw.intValue();
        } catch (Exception ex) {
            // 这里不抛：让上层决定是降级还是报错。返回 NOT_READY 会走到「秒杀暂时不可用」。
            log.warn("Redis 预扣失败，itemId={}, userId={}", itemId, userId, ex);
            return NOT_READY;
        }
    }

    /**
     * 回补：数据库落库失败时把 Redis 的名额还回去。
     *
     * 只有「Redis 扣成功、DB 失败」这条窄路上才会调用。
     *
     * 判断标准：Lua 脚本里有几次写操作，这里就要撤几次。
     * 脚本写了 DECR（库存）和 SET（一人一单标记），所以这里必须还两样。
     * 只还库存不删标记的后果：这个用户重试会被判成「已购买」，永远抢不到。
     *
     * 另外 INCR 一个不存在的 key 会凭空造出 stock=1 的幽灵库存，
     * 所以加库存之前要先确认 key 还在。
     */
    public void rollback(Long itemId, Long userId) {
        release(itemId, userId);
    }

    /**
     * 释放一个名额：Redis 库存加回 1 + 删掉一人一单标记。
     *
     * 两个场景共用：DB 落库失败的回补、用户取消订单后的释放。
     * 语义上是同一件事 —— 这次占用作废，名额还给池子。
     *
     * 只能在 DB 那边已经成功回补之后调用。反过来先动 Redis 再改 DB，
     * 一旦 DB 失败，Redis 就多出一个名额，直接超卖；
     * 而 DB 成功、Redis 失败只是少卖一个，下次对账也不会帮倒忙（对账只往下修）。
     */
    public void release(Long itemId, Long userId) {
        try {
            if (Boolean.TRUE.equals(redis.hasKey(stockKey(itemId)))) {
                redis.opsForValue().increment(stockKey(itemId));
            }
            redis.delete(userKey(itemId, userId));
        } catch (Exception ex) {
            // 绝对不能往外抛：此时上层正在处理 DB 的异常，
            // 这里再抛一次会把真正的错误盖掉，排查方向全错。
            log.error("秒杀名额释放失败，需要人工对账 itemId={}, userId={}", itemId, userId, ex);
        }
    }

    /**
     * 供 Controller 查询当前 Redis 里的库存。
     *
     * @return null 表示还没预热（key 不存在），这不是异常
     */
    public Integer currentStock(Long itemId) {
        String stock = redis.opsForValue().get(stockKey(itemId));
        if (stock == null) {
            return null;
        }
        return Integer.valueOf(stock);
    }

    private String stockKey(Long itemId) {
        return STOCK_KEY_PREFIX + itemId;
    }

    private String userKey(Long itemId, Long userId) {
        return USER_KEY_PREFIX + itemId + ":" + userId;
    }

    /**
     * 对账修正：只在 Redis 库存「多于」DB 时才下调。
     *
     * 必须是 Lua：GET / 比较 / SET 三步之间不能插入别的扣减，
     * 否则刚比较完就被扣了一个，SET 又把多扣的那一个还回去了。
     */
    private static final String RESET_SCRIPT = """
            local cur = redis.call('GET', KEYS[1])
            local db = tonumber(ARGV[1])
            if not cur then
                redis.call('SET', KEYS[1], db, 'EX', ARGV[2])
                return 1
            end
            if tonumber(cur) > db then
                redis.call('SET', KEYS[1], db, 'EX', ARGV[2])
                return 2
            end
            return 0
            """;

    /**
     * 对账修正：只在 Redis 库存「多于」DB 时才下调。
     *
     * 方向和业务安全直接相关，不能写反：Redis 偏多意味着超卖风险，必须压下来；
     * Redis 偏少通常只是「队列里还有订单没落库」，这时候补上去等于把已卖出的名额再放一遍。
     *
     * @return 1=key 不存在已重建；2=检测到漂移已下调；0=无需处理
     */
    public int resetStock(Long itemId, int dbStock) {
        try {
            Long raw = redis.execute(resetScript, List.of(stockKey(itemId)),
                    String.valueOf(dbStock), String.valueOf(STOCK_TTL.toSeconds()));
            return raw == null ? 0 : raw.intValue();
        } catch (Exception ex) {
            // 对账失败不能影响业务，30 秒后还会再来一轮
            log.warn("库存对账修正失败，itemId={}", itemId, ex);
            return 0;
        }
    }

}
