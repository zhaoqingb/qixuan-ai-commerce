package com.qixuan.commerce.service;

import com.qixuan.commerce.entity.SeckillActivity;
import com.qixuan.commerce.entity.SeckillItem;
import com.qixuan.commerce.mapper.SeckillActivityMapper;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 秒杀商品与活动的本地缓存。
 *
 * 为什么需要：buy() 每次请求要查两次主键（item + activity），
 * 1000 并发就是 2000 次数据库往返，而且抢不到的人也要白查一遍。
 *
 * 为什么用 TTL 而不是主动失效：秒杀期间商品信息几乎不变，
 * 5 秒自然过期足够，省掉「改价时通知所有实例」这套复杂度。
 * 代价是最多 5 秒的陈旧窗口 —— 对秒杀来说可以接受。
 *
 * 注意：这个缓存是单机内存，多实例部署时各存各的。
 * 秒杀场景通常一个活动由一个实例扛，暂时不构成问题。
 */
@Service
public class SeckillItemCache {

    /** 缓存存活时间。别调太大：活动上/下线的变化要在这个时间内生效 */
    private static final long TTL_MS = 5_000L;

    private final SeckillItemMapper itemMapper;
    private final SeckillActivityMapper activityMapper;

    /** 每个 itemId 一份，读多写少；ConcurrentHashMap 的读操作不加锁 */
    private final ConcurrentHashMap<Long, Cached> cache = new ConcurrentHashMap<>();

    public SeckillItemCache(SeckillItemMapper itemMapper, SeckillActivityMapper activityMapper) {
        this.itemMapper = itemMapper;
        this.activityMapper = activityMapper;
    }

    /** 缓存值用 record 装：天生不可变，不用担心被调用方改坏 */
    public record Cached(SeckillItem item, SeckillActivity activity, long loadedAt) {
    }

    /**
     * 取缓存，命中且未过期直接返回，否则查库重建。
     *
     * @return null 表示商品不存在
     */
    public Cached get(Long itemId) {
        long now = System.currentTimeMillis();
        Cached hit = cache.get(itemId);

        if (hit != null && now - hit.loadedAt() < TTL_MS) {
            return hit;
        }
        return reload(itemId);
    }

    /** 强制失效，运维或对账时用 */
    public void evict(Long itemId) {
        cache.remove(itemId);
    }

    /**
     * 查库重建缓存。
     *
     * 并发下可能有多个线程同时走到这里，各自查一次库、各自 put 一次 —— 无害：
     * 它们查到的数据一样，最后写入的那个赢，不会产生错误状态。
     * 用 computeIfAbsent 会持锁查库，反而把并发读拖慢，不划算。
     */
    private Cached reload(Long itemId) {
        SeckillItem item = itemMapper.selectById(itemId);
        if (item == null) {
            // 商品不存在不写缓存：缓 null 值要额外的空值标记，不缓存又每次穿透，
            // 这里选择不缓存 —— 商品 id 不存在的请求本来就极少
            return null;
        }
        SeckillActivity activity = activityMapper.selectById(item.getActivityId());
        Cached fresh = new Cached(item, activity, System.currentTimeMillis());
        cache.put(itemId, fresh);
        return fresh;
    }
}
