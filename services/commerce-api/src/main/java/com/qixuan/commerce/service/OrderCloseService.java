package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qixuan.commerce.dto.CancelOrderResponse;
import com.qixuan.commerce.entity.OrderItem;
import com.qixuan.commerce.entity.StoreOrder;
import com.qixuan.commerce.entity.TradeOrder;
import com.qixuan.commerce.mapper.InventoryLogMapper;
import com.qixuan.commerce.mapper.OrderItemMapper;
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
public class OrderCloseService {

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String CANCELLED = "CANCELLED";

    private final TradeOrderMapper tradeOrderMapper;
    private final StoreOrderMapper storeOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SkuInventoryMapper skuInventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;

    public OrderCloseService(TradeOrderMapper tradeOrderMapper,
            StoreOrderMapper storeOrderMapper,
            OrderItemMapper orderItemMapper,
            SkuInventoryMapper skuInventoryMapper,
            InventoryLogMapper inventoryLogMapper) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.storeOrderMapper = storeOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.skuInventoryMapper = skuInventoryMapper;
        this.inventoryLogMapper = inventoryLogMapper;
    }

    /**
     * 用户主动取消。
     * 注意：这里也必须加 @Transactional —— 类内部调用 closeOrder() 不走 Spring 代理，
     * 只靠 closeOrder 上的注解是无效的，得由外层方法把事务撑起来。
     */
    @Transactional(rollbackFor = Exception.class)
    public CancelOrderResponse cancel(String orderNo, Long userId) {
        TradeOrder order = tradeOrderMapper.selectOne(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getOrderNo, orderNo)
                        .eq(TradeOrder::getUserId, userId));
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (!PENDING_PAYMENT.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "当前订单状态不可取消：" + order.getStatus());
        }

        LocalDateTime closedAt = closeOrder(order);
        if (closedAt == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试");
        }
        return new CancelOrderResponse(orderNo, CANCELLED, closedAt);
    }

    /**
     * 单笔关单：状态 CAS → 子单同步 → 库存 locked 退回 available。
     * 返回关闭时间；返回 null 表示状态已被别人改过（比如刚付款），调用方跳过即可。
     * 用户取消和超时自动关单共用这一段。
     */
    @Transactional(rollbackFor = Exception.class)
    public LocalDateTime closeOrder(TradeOrder order) {
        LocalDateTime now = LocalDateTime.now();

        TradeOrder patch = new TradeOrder();
        patch.setStatus(CANCELLED);
        patch.setClosedAt(now);
        int rows = tradeOrderMapper.update(patch,
                new LambdaUpdateWrapper<TradeOrder>()
                        .eq(TradeOrder::getId, order.getId())
                        .eq(TradeOrder::getStatus, PENDING_PAYMENT));
        if (rows == 0) {
            return null;
        }

        StoreOrder subPatch = new StoreOrder();
        subPatch.setStatus(CANCELLED);
        storeOrderMapper.update(subPatch,
                new LambdaUpdateWrapper<StoreOrder>()
                        .eq(StoreOrder::getTradeOrderId, order.getId())
                        .eq(StoreOrder::getStatus, PENDING_PAYMENT));

        List<StoreOrder> subs = storeOrderMapper.selectList(
                new LambdaQueryWrapper<StoreOrder>()
                        .eq(StoreOrder::getTradeOrderId, order.getId()));
        for (StoreOrder sub : subs) {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>()
                            .eq(OrderItem::getStoreOrderId, sub.getId()));
            for (OrderItem item : items) {
                if (skuInventoryMapper.releaseStock(item.getSkuId(), item.getQuantity()) == 0) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "库存锁定数量不足：" + item.getProductTitle());
                }
                inventoryLogMapper.insert(item.getSkuId(), order.getOrderNo(), "RELEASE",
                        item.getQuantity());
            }
        }
        return now;
    }
}
