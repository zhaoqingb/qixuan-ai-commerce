package com.qixuan.commerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.qixuan.commerce.entity.SeckillOrder;

/**
 * Kafka 消费失败的处理策略。
 *
 * 没有这个配置时，消费方法抛异常会让 Spring Kafka 用默认策略重试（1 秒间隔、最多 9 次），
 * 耗尽之后**只打一行日志就把消息跳过并提交 offset** —— 消息实际上丢了。
 *
 * 这里的策略是：重试 3 次，还不行就投递到死信队列（DLT，topic 名是 `seckill-order.DLT`）。
 * 死信消息留在 Kafka 里，可以人工排查后重放，而不是消失。
 *
 * 为什么重试次数要比默认值小：
 * 秒杀场景下，落库失败几乎不可能是"临时抖动"（数据库重启之类的短暂故障很少见），
 * 更可能是数据问题（库存账已经漂了）。继续重试只是拖延，早点进 DLT 早点被人看到。
 */
@Configuration
public class KafkaConsumerConfig {

    /** 重试间隔：1 秒 */
    private static final long RETRY_INTERVAL_MS = 1000L;

    /** 重试次数：3 次（不含首次），总共最多尝试 4 次 */
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, SeckillOrder> kafkaTemplate) {
        // 默认行为：把失败的消息原样发到 <原topic>.DLT，分区和 key 保持不变
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));

        // 反序列化失败的消息没法重新投递（连对象都构造不出来），直接跳过，
        // 否则这类"毒消息"会让整个分区彻底卡死
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class);

        return handler;
    }
}
