package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qixuan.commerce.entity.TradeOrder;
import com.qixuan.commerce.mapper.TradeOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未付款自动关单。每分钟扫一次，逐笔调用 OrderCloseService
 * （跨类调用才会走事务代理；单笔失败只记日志，不影响其它订单）。
 */
@Component
public class OrderCloseJob {

    private static final Logger log = LoggerFactory.getLogger(OrderCloseJob.class);
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";

    private final TradeOrderMapper tradeOrderMapper;
    private final OrderCloseService orderCloseService;
    private final long timeoutMinutes;

    public OrderCloseJob(TradeOrderMapper tradeOrderMapper,
            OrderCloseService orderCloseService,
            @Value("${qixuan.order.pay-timeout-minutes:1440}") long timeoutMinutes) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.orderCloseService = orderCloseService;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1M")
    public void closeExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<TradeOrder> expired = tradeOrderMapper.selectList(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getStatus, PENDING_PAYMENT)
                        .lt(TradeOrder::getCreatedAt, deadline));

        for (TradeOrder order : expired) {
            try {
                if (orderCloseService.closeOrder(order) != null) {
                    log.info("订单超时关单成功：{}", order.getOrderNo());
                }
            } catch (Exception e) {
                log.error("订单超时关单失败：{}", order.getOrderNo(), e);
            }
        }
    }
}
