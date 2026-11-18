package com.qixuan.commerce.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.qixuan.commerce.entity.SeckillOrder;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import com.qixuan.commerce.mapper.SeckillOrderMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class SeckillOrderWriter {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderWriter.class);
    private static final int BATCH_SIZE = 200;
    private static final int QUEUE_CAPACITY = 20000;
    private final BlockingQueue<SeckillOrder> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    /** 后台写入线程，抽成字段是为了能在 @PreDestroy 里中断它 */
    private Thread worker;
    private final SeckillOrderMapper orderMapper;
    private final SeckillItemMapper itemMapper;
    private final SeckillStockService stockService;
    private final TransactionTemplate transactionTemplate;

    public SeckillOrderWriter(SeckillOrderMapper orderMapper, SeckillItemMapper itemMapper,
            SeckillStockService stockService, TransactionTemplate transactionTemplate) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.stockService = stockService;
        this.transactionTemplate = transactionTemplate;
    }

    public boolean enqueue(SeckillOrder order) {
        return queue.offer(order);
    }

    @PostConstruct
    public void start() {
        worker = new Thread(this::loop, "seckill-order-writer");
        worker.setDaemon(true);
        worker.start();
        log.info("秒杀订单异步写入进程已启动，批量上限：{}", BATCH_SIZE);
    }

    /**
     * 容器关闭时停掉后台线程。
     *
     * 这个线程是手动 new 的，不在 Spring 的管理范围内 —— 不加这个方法，
     * devtools 每次热部署都会留下一个僵尸线程，卡在旧队列的 take() 上永不退出。
     *
     * interrupt() 会让 take() 抛 InterruptedException，被 loop() 捕获后 return 退出。
     */
    @PreDestroy
    public void stop() {
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<SeckillOrder> batch = new ArrayList<>(BATCH_SIZE);
                batch.add(queue.take());
                queue.drainTo(batch, BATCH_SIZE - 1);
                flush(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("秒杀订单批量写入异常", e);
            }
        }
    }

    private void flush(List<SeckillOrder> batch) {
        if (batch.isEmpty()) {
            return;
        }
        Map<Long, Integer> countByItem = new HashMap<>();
        for (SeckillOrder order : batch) {
            countByItem.merge(order.getItemId(), 1, Integer::sum);
        }
        try {
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
        } catch (Exception ex) {
            log.error("批量落库失败，回补Redis{}条", batch.size(), ex);
            for (SeckillOrder order : batch) {
                stockService.rollback(order.getItemId(), order.getUserId());
            }
        }
    }
}
