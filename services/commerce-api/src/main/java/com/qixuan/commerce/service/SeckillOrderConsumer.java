package com.qixuan.commerce.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
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
     *
     * **参数必须是 List<ConsumerRecord> 而不是 List<SeckillOrder>**：
     * 后者框架会把消息记录拆成纯 payload，丢掉 topic/partition/offset，
     * 导致 DeadLetterPublishingRecoverer 无法投递到死信队列 ——
     * 它需要这些信息才能构造死信消息。表现为「重试无数遍、死信一直是空的」。
     */
    @KafkaListener(topics = SeckillOrderProducer.TOPIC)
    public void onMessage(List<ConsumerRecord<String, SeckillOrder>> records, Acknowledgment ack) {
        if (records == null || records.isEmpty()) {
            ack.acknowledge();
            return;
        }

        List<SeckillOrder> orders = new ArrayList<>(records.size());
        for (ConsumerRecord<String, SeckillOrder> record : records) {
            orders.add(record.value());
        }

        try {
            // 快路径：整批一次事务，绝大多数情况下走这里
            orderWriter.writeBatch(orders);
            ack.acknowledge();
        } catch (Exception batchEx) {
            // 慢路径：整批失败时降级逐条重做，把坏消息单独挑出来。
            // 为什么不直接整批抛：一条毒消息会把 199 条正常订单一起拖进死信。
            log.warn("整批落库失败，降级为逐条处理，批量 {} 条", records.size(), batchEx);
            writeIndividuallyOrThrow(records);
            ack.acknowledge();
        }
    }

    /**
     * 逐条落库，遇到坏消息就抛 BatchListenerFailedException。
     *
     * 这是批量消费配合死信队列的**唯一正确方式**：
     * 异常里带上 index，框架才知道"前面几条已经成功、第几条失败"，
     * 从而提交前序 offset，只把失败的那条投递到死信 topic，其余继续消费。
     *
     * 如果只是普通地 throw，框架无法定位批内位置，只能 seek 回去整批重投 ——
     * 表现为「日志一直刷、offset 永远不动、死信永远是空的」。
     */
    private void writeIndividuallyOrThrow(List<ConsumerRecord<String, SeckillOrder>> records) {
        for (int index = 0; index < records.size(); index++) {
            ConsumerRecord<String, SeckillOrder> record = records.get(index);
            try {
                orderWriter.writeBatch(List.of(record.value()));
            } catch (Exception singleEx) {
                log.error("第 {} 条落库失败，投递死信 partition={} offset={} orderNo={}",
                        index, record.partition(), record.offset(),
                        record.value() == null ? "null" : record.value().getOrderNo(),
                        singleEx);
                throw new BatchListenerFailedException("单条落库失败", singleEx, index);
            }
        }
    }
}
