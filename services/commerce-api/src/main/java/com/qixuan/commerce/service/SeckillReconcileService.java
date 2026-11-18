package com.qixuan.commerce.service;

import com.qixuan.commerce.entity.SeckillItem;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 秒杀库存对账：定期用 DB 的账面数字校正 Redis。
 *
 * 为什么必须有：Redis 会丢数据（重启、主从切换、误删、内存满被淘汰），
 * 一旦 Redis 的库存「多于」DB，就会超卖 —— 这是电商唯一不能容忍的错误。
 *
 * 为什么只往下修不往上修：Redis 比 DB 少，通常是「队列里还有订单没落库」的正常中间状态。
 * 往上补会把已经卖出去的名额再放出来，直接导致超卖。
 */
@Service
public class SeckillReconcileService {

    private static final Logger log = LoggerFactory.getLogger(SeckillReconcileService.class);

    /** 30 秒一轮。太频繁浪费资源，太稀疏则漂移窗口太长 */
    private static final long INTERVAL_MS = 30_000L;

    private final SeckillItemMapper itemMapper;
    private final SeckillStockService stockService;

    public SeckillReconcileService(SeckillItemMapper itemMapper, SeckillStockService stockService) {
        this.itemMapper = itemMapper;
        this.stockService = stockService;
    }

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = 10_000L)
    public void reconcile() {
        // 秒杀商品数量很少（一场活动几个到几十个），全表扫描的成本可以忽略。
        // 如果以后商品上千，要改成只查「当前进行中活动」下的 item。
        List<SeckillItem> items = itemMapper.selectList(null);
        for (SeckillItem item : items) {
            reconcileOne(item);
        }
    }

    private void reconcileOne(SeckillItem item) {
        Integer redisStock = stockService.currentStock(item.getId());
        int dbStock = item.getSeckillStock();

        if (redisStock == null) {
            // key 丢了 = Redis 重启过或没预热，这不是「漂移」，是「从零建立」
            stockService.loadStock(item.getId(), dbStock);
            log.info("秒杀库存 key 缺失，已按 DB 重建 itemId={} stock={}", item.getId(), dbStock);
            return;
        }

        if (redisStock > dbStock) {
            // 最危险的方向：Redis 凭空多出名额，不处理就会超卖
            log.error("秒杀库存漂移，Redis 多于 DB，存在超卖风险！itemId={} redis={} db={}",
                    item.getId(), redisStock, dbStock);
            stockService.resetStock(item.getId(), dbStock);
            return;
        }

        if (redisStock < dbStock) {
            // 正常中间状态：队列里还有订单没落库。绝不能往上补。
            log.debug("Redis 库存偏少（队列中可能有未落库订单）itemId={} redis={} db={}",
                    item.getId(), redisStock, dbStock);
        }
    }
}
