package com.qixuan.commerce.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qixuan.commerce.dto.RefundApplyRequest;
import com.qixuan.commerce.dto.RefundApplyResponse;
import com.qixuan.commerce.entity.OrderItem;
import com.qixuan.commerce.entity.RefundRequest;
import com.qixuan.commerce.entity.Store;
import com.qixuan.commerce.entity.StoreOrder;
import com.qixuan.commerce.entity.TradeOrder;
import com.qixuan.commerce.mapper.InventoryLogMapper;
import com.qixuan.commerce.mapper.OrderItemMapper;
import com.qixuan.commerce.mapper.RefundRequestMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;
import com.qixuan.commerce.mapper.StoreMapper;
import com.qixuan.commerce.mapper.StoreOrderMapper;
import com.qixuan.commerce.mapper.TradeOrderMapper;

@Service
public class RefundService {

    /** 只有付过款、货还/还在路上的订单才能退 */
    private static final Set<String> REFUNDABLE_STATUS = Set.of("PAID", "SHIPPED", "COMPLETED");

    private static final String STATUS_REQUESTED = "REQUESTED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_REFUNDED = "REFUNDED";

    private final RefundRequestMapper refundRequestMapper;
    private final OrderItemMapper orderItemMapper;
    private final StoreOrderMapper storeOrderMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final StoreMapper storeMapper;
    private final SkuInventoryMapper skuInventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;

    public RefundService(RefundRequestMapper refundRequestMapper, OrderItemMapper orderItemMapper,
            StoreOrderMapper storeOrderMapper, TradeOrderMapper tradeOrderMapper,
            StoreMapper storeMapper, SkuInventoryMapper skuInventoryMapper,
            InventoryLogMapper inventoryLogMapper) {
        this.refundRequestMapper = refundRequestMapper;
        this.orderItemMapper = orderItemMapper;
        this.storeOrderMapper = storeOrderMapper;
        this.tradeOrderMapper = tradeOrderMapper;
        this.storeMapper = storeMapper;
        this.skuInventoryMapper = skuInventoryMapper;
        this.inventoryLogMapper = inventoryLogMapper;
    }

    @Transactional
    public RefundApplyResponse apply(Long userId, RefundApplyRequest request) {
        OrderItem item = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getId, request.orderItemId()).last("FOR UPDATE"));
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单明细不存在");

        }
        StoreOrder sub = storeOrderMapper.selectById(item.getStoreOrderId());
        if (sub == null || !userId.equals(sub.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单明细不存在");
        }
        if (!REFUNDABLE_STATUS.contains(sub.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前订单状态不可退款");
        }

        int occupied = refundRequestMapper.sumRefundedQuantity(item.getId());
        int total = item.getQuantity();
        int refundable = total - occupied;
        if (request.quantity() > refundable) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多还能退 " + refundable + " 件");
        }

        BigDecimal amount = item.getUnitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        RefundRequest refund = new RefundRequest();
        refund.setRefundNo(generateRefundNo());
        refund.setOrderItemId(item.getId());
        refund.setStoreOrderId(sub.getId());
        refund.setUserId(userId);
        refund.setQuantity(request.quantity());
        refund.setRefundAmount(amount);
        refund.setReason(request.reason());
        refund.setStatus(STATUS_REQUESTED);
        refundRequestMapper.insert(refund);
        return new RefundApplyResponse(refund.getRefundNo(), refund.getQuantity(), refund.getRefundAmount(),
                refund.getStatus());

    }

    private String generateRefundNo() {
        return "R" + IdWorker.getIdStr();
    }

    @Transactional
    public void approve(Long userId, Long refundId) {
        RefundRequest refund = refundRequestMapper.selectById(refundId);
        if (refund == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "退款申请不存在");
        }
        StoreOrder sub = storeOrderMapper.selectById(refund.getStoreOrderId());
        if (sub == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        Store store = storeMapper.selectById(sub.getStoreId());
        if (store == null || !userId.equals(store.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在");
        }

        RefundRequest patch = new RefundRequest();
        patch.setStatus(STATUS_REFUNDED);
        patch.setResolvedAt(LocalDateTime.now());

        int rows = refundRequestMapper.update(patch, new LambdaUpdateWrapper<RefundRequest>()
                .eq(RefundRequest::getId, refundId).eq(RefundRequest::getStatus, STATUS_REQUESTED));

        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "退款申请已被处理");
        }

        if ("PAID".equals(sub.getStatus())) {
            OrderItem item = orderItemMapper.selectById(refund.getOrderItemId());
            if (skuInventoryMapper.refundStock(item.getSkuId(), refund.getQuantity()) == 0) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "库存回滚失败");
            }
            inventoryLogMapper.insert(item.getSkuId(), refund.getRefundNo(), "REFUND", refund.getQuantity());
        }

        // 子单退完 → 子单改 REFUNDED；所有子单都退完 → 主单也改
        if (isFullRefunded(sub.getId())) {
            StoreOrder patchSub = new StoreOrder();
            patchSub.setId(sub.getId());
            patchSub.setStatus(STATUS_REFUNDED);
            storeOrderMapper.updateById(patchSub);

            if (isTradeOrderFullyRefunded(sub.getTradeOrderId())) {
                TradeOrder patchTrade = new TradeOrder();
                patchTrade.setId(sub.getTradeOrderId());
                patchTrade.setStatus(STATUS_REFUNDED);
                tradeOrderMapper.updateById(patchTrade);
            }
        }
    }

    /** 主单下所有子单都是 REFUNDED，主单才算整体退完 */
    private boolean isTradeOrderFullyRefunded(Long tradeOrderId) {
        List<StoreOrder> subs = storeOrderMapper.selectList(
                new LambdaQueryWrapper<StoreOrder>()
                        .eq(StoreOrder::getTradeOrderId, tradeOrderId));
        for (StoreOrder sub : subs) {
            if (!STATUS_REFUNDED.equals(sub.getStatus())) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public void reject(Long userId, Long refundId) {
        RefundRequest refund = refundRequestMapper.selectById(refundId);
        if (refund == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "退款申请不存在");
        }
        StoreOrder sub = storeOrderMapper.selectById(refund.getStoreOrderId());
        if (sub == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        Store store = storeMapper.selectById(sub.getStoreId());
        if (store == null || !userId.equals(store.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在");
        }
        RefundRequest patch = new RefundRequest();
        patch.setStatus(STATUS_REJECTED);
        patch.setResolvedAt(LocalDateTime.now());
        int rows = refundRequestMapper.update(patch, new LambdaUpdateWrapper<RefundRequest>()
                .eq(RefundRequest::getId, refundId).eq(RefundRequest::getStatus, STATUS_REQUESTED));
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "退款申请已被处理");
        }

    }

    /**
     * 子单下的每一条明细是否都退完了。
     *
     * 两个关键点：
     *   1. 累加的是退款单的 quantity（件数），不是条数（count）
     *   2. 累加变量放在循环内，否则上一条明细的退款数会污染下一条的判断
     */
    private boolean isFullRefunded(Long storeOrderId) {

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getStoreOrderId, storeOrderId));

        for (OrderItem item : items) {
            List<RefundRequest> refunds = refundRequestMapper.selectList(
                    new LambdaQueryWrapper<RefundRequest>()
                            .eq(RefundRequest::getOrderItemId, item.getId())
                            .in(RefundRequest::getStatus, STATUS_APPROVED, STATUS_REFUNDED));

            int refunded = 0;
            for (RefundRequest refund : refunds) {
                refunded += refund.getQuantity();
            }

            if (refunded < item.getQuantity()) {
                return false;
            }
        }
        return true;
    }
}
