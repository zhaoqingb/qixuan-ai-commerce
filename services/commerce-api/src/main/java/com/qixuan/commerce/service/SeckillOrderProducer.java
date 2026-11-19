package com.qixuan.commerce.service;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.qixuan.commerce.entity.SeckillOrder;

/**
 * 秒杀订单投递到 Kafka。
 *
 * 这里选择**同步发送**（等 broker 返回 ack），不是异步。
 *
 * 异步更快，但发送失败时用户已经收到"抢购成功"，而订单其实没进队列 ——
 * 那一刻只能回补名额，用户白高兴一场。
 *
 * 同步多花 1~3ms（本地 broker），换来的是：
 * 投递失败能当场发现、当场回补、当场告诉用户"稍后再试"。
 * 抢购路径本来就不该把不确定性往后推。
 */
@Service
public class SeckillOrderProducer {

    /** Topic 名。分区数在建 Topic 时定了（3 个），它决定了消费并行度的上限 */
    public static final String TOPIC = "seckill-order";

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderProducer.class);

    /** broker 迟迟不响应就别等了，否则抢购线程全卡在这 */
    private static final long SEND_TIMEOUT_SECONDS = 3L;

    private final KafkaTemplate<String, SeckillOrder> kafkaTemplate;

    public SeckillOrderProducer(KafkaTemplate<String, SeckillOrder> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 投递一条订单消息。
     *
     * 分区键用 userId：同一个用户的消息落在同一分区（保持顺序），
     * 不同用户之间均匀分散到 3 个分区，实现并行消费。
     *
     * @return true 表示消息已被 broker 接收；false 表示没发出去，调用方必须回补名额
     */
    public boolean send(SeckillOrder order) {
        String key = String.valueOf(order.getUserId());
        try {
            kafkaTemplate.send(TOPIC, key, order).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("秒杀订单投递被中断 orderNo={}", order.getOrderNo(), ex);
            return false;
        } catch (Exception ex) {
            log.error("秒杀订单投递 Kafka 失败，需要回补名额 orderNo={}", order.getOrderNo(), ex);
            return false;
        }
    }
}
