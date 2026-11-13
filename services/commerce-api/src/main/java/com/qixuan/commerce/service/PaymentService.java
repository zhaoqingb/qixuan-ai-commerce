package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qixuan.commerce.dto.PayOrderResponse;
import com.qixuan.commerce.entity.OrderItem;
import com.qixuan.commerce.entity.StoreOrder;
import com.qixuan.commerce.entity.TradeOrder;
import com.qixuan.commerce.mapper.InventoryLogMapper;
import com.qixuan.commerce.mapper.OrderItemMapper;
import com.qixuan.commerce.mapper.PaymentRecordMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;
import com.qixuan.commerce.mapper.StoreOrderMapper;
import com.qixuan.commerce.mapper.TradeOrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String PAID = "PAID";
    private static final String PROVIDER_MOCK = "MOCK";

    private final TradeOrderMapper tradeOrderMapper;
    private final StoreOrderMapper storeOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SkuInventoryMapper skuInventoryMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final InventoryLogMapper inventoryLogMapper;

    public PaymentService(TradeOrderMapper tradeOrderMapper,
            StoreOrderMapper storeOrderMapper,
            OrderItemMapper orderItemMapper,
            SkuInventoryMapper skuInventoryMapper,
            PaymentRecordMapper paymentRecordMapper,
            InventoryLogMapper inventoryLogMapper) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.storeOrderMapper = storeOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.skuInventoryMapper = skuInventoryMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.inventoryLogMapper = inventoryLogMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public PayOrderResponse pay(String orderNo, Long userId) {
        TradeOrder order = tradeOrderMapper.selectOne(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getOrderNo, orderNo)
                        .eq(TradeOrder::getUserId, userId));
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }

        // 幂等：渠道回调会重复推送，已付的直接返回原结果，绝不重复迁库存
        if (PAID.equals(order.getStatus())) {
            return new PayOrderResponse(order.getOrderNo(), order.getStatus(),
                    order.getPayableAmount(), order.getPaidAt());
        }
        if (!PENDING_PAYMENT.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "当前订单状态不可支付：" + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();

        // 状态机 CAS：where 带上旧状态，并发重复点击只有一个能改成功
        TradeOrder patch = new TradeOrder();
        patch.setStatus(PAID);
        patch.setPaidAt(now);
        int rows = tradeOrderMapper.update(patch,
                new LambdaUpdateWrapper<TradeOrder>()
                        .eq(TradeOrder::getId, order.getId())
                        .eq(TradeOrder::getStatus, PENDING_PAYMENT));
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试");
        }

        // 子单跟着流转，否则列表页每个店铺的状态会不一致
        StoreOrder subPatch = new StoreOrder();
        subPatch.setStatus(PAID);
        storeOrderMapper.update(subPatch,
                new LambdaUpdateWrapper<StoreOrder>()
                        .eq(StoreOrder::getTradeOrderId, order.getId())
                        .eq(StoreOrder::getStatus, PENDING_PAYMENT));

        // 库存三段式：下单 available→locked，付款 locked→sold
        List<StoreOrder> subs = storeOrderMapper.selectList(
                new LambdaQueryWrapper<StoreOrder>()
                        .eq(StoreOrder::getTradeOrderId, order.getId()));
        for (StoreOrder sub : subs) {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>()
                            .eq(OrderItem::getStoreOrderId, sub.getId()));
            for (OrderItem item : items) {
                if (skuInventoryMapper.confirmStock(item.getSkuId(), item.getQuantity()) == 0) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "库存锁定数量不足：" + item.getProductTitle());
                }
                inventoryLogMapper.insert(item.getSkuId(), order.getOrderNo(), "CONFIRM",
                        item.getQuantity());
            }
        }

        paymentRecordMapper.insert("P" + order.getOrderNo(), order.getId(),
                PROVIDER_MOCK, null, order.getPayableAmount(), "SUCCESS", now);

        return new PayOrderResponse(order.getOrderNo(), PAID, order.getPayableAmount(), now);
    }
}
