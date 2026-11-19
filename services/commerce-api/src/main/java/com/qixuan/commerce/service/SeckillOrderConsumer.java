package com.qixuan.commerce.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.qixuan.commerce.entity.SeckillOrder;

/**
 * 秒杀订单消费端。
 *
 * 批量模式：一次 poll 拉 max-poll-records（配置里是 200）条，作为一个 List 交进来。
 * 这和之前内存队列的 drainTo 攒批是同一个目的 —— 把 N 个订单合成一次事务写下去，
 * 把 fsync 次数从 N 压到 1。
 */
@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final SeckillOrderWriter orderWriter;

    public SeckillOrderConsumer(SeckillOrderWriter orderWriter) {
        this.orderWriter = orderWriter;
    }

    /**
     * ack 的时机是这段代码的全部要点：**业务成功之后才 ack**。
     *
     * - 提前 ack：处理失败时消息已标记消费，订单永久消失
     * - 成功才 ack：失败就抛异常，offset 不提交，Kafka 重投整批
     *
     * 重投会重复处理，但 INSERT IGNORE + uk_seckill_user_item 保证幂等。
     * 这就是"Mq 是至少一次投递，消费端必须自己幂等"的落地。
     */
    @KafkaListener(topics = SeckillOrderProducer.TOPIC)
    public void onMessage(List<SeckillOrder> orders, Acknowledgment ack) {
        if (orders == null || orders.isEmpty()) {
            ack.acknowledge();
            return;
        }

        try {
            orderWriter.writeBatch(orders);
            ack.acknowledge();
        } catch (Exception ex) {
            // 不 ack、不吞异常：让 Spring Kafka 的错误处理器重试，
            // 重试次数耗尽后仍失败的消息会被记录，需要人工介入（后续可接死信队列）
            log.error("秒杀订单批量落库失败，本批 {} 条交由 Kafka 重投", orders.size(), ex);
            throw ex;
        }
    }
}
