package com.qixuan.commerce.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.qixuan.commerce.entity.SeckillOrder;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import com.qixuan.commerce.mapper.SeckillOrderMapper;

/**
 * 秒杀订单批量落库。
 *
 * 历史上这里还有一个 JVM 内存队列 + 后台线程（take + drainTo 攒批）。
 * 换成 Kafka 之后队列和线程都删了，原因：
 *   1. 消息存在 Kafka 磁盘上，进程崩溃不丢单
 *   2. 多实例部署时 Kafka 自动分配分区，内存队列做不到
 *   3. 攒批能力由 Kafka 的 max.poll.records 提供，不需要自己实现
 *
 * 这里只保留一件仍然必要的事：**把一批订单合成一次事务写下去**。
 * 500 个订单 500 个事务 = 500 次 fsync；合成 3 个批次 = 几十次，这是 2400 QPS 的来源。
 */
@Service
public class SeckillOrderWriter {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderWriter.class);

    private final SeckillOrderMapper orderMapper;
    private final SeckillItemMapper itemMapper;
    private final TransactionTemplate transactionTemplate;

    public SeckillOrderWriter(SeckillOrderMapper orderMapper, SeckillItemMapper itemMapper,
            TransactionTemplate transactionTemplate) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 批量写入。
     *
     * 失败时**不回补 Redis，直接抛异常** —— 这是和内存队列版本最重要的区别。
     *
     * 内存队列时代：落库失败只能回补 Redis，否则名额白白蒸发。但代价是用户抢到了却没有订单。
     * Kafka 时代：抛异常 → offset 不提交 → 消息还在 → 重启或重试后能再处理一次。
     *            配合 INSERT IGNORE + 唯一索引，重复处理是安全的。
     *
     * 换句话说：**Kafka 让我们可以把"失败"变成"稍后重试"，而不是"就地作废"。**
     */
    public void writeBatch(List<SeckillOrder> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        Map<Long, Integer> countByItem = new HashMap<>();
        for (SeckillOrder order : batch) {
            countByItem.merge(order.getItemId(), 1, Integer::sum);
        }

        transactionTemplate.executeWithoutResult(status -> {
            for (Map.Entry<Long, Integer> entry : countByItem.entrySet()) {
                int rows = itemMapper.deductStock(entry.getKey(), entry.getValue());
                if (rows != 1) {
                    throw new IllegalStateException(
                            "批量扣库存失败，itemId=" + entry.getKey() + "，需要" + entry.getValue() + "件");
                }
            }
            orderMapper.insertBatch(batch);
        });

        log.info("秒杀订单批量落库成功，共{}条", batch.size());
    }
}
