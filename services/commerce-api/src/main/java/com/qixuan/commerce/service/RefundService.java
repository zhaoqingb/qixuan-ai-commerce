package com.qixuan.commerce.service;

import com.qixuan.commerce.dto.RefundApplyRequest;
import com.qixuan.commerce.dto.RefundApplyResponse;
import com.qixuan.commerce.entity.OrderItem;
import com.qixuan.commerce.entity.RefundRequest;
import com.qixuan.commerce.entity.StoreOrder;
import com.qixuan.commerce.entity.Store;
import com.qixuan.commerce.mapper.InventoryLogMapper;
import com.qixuan.commerce.mapper.OrderItemMapper;
import com.qixuan.commerce.mapper.RefundRequestMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;
import com.qixuan.commerce.mapper.StoreMapper;
import com.qixuan.commerce.mapper.StoreOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RefundService {

    private static final DateTimeFormatter REFUND_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 只有付过款、货还/还在路上的订单才能退 */
    private static final Set<String> REFUNDABLE_STATUS = Set.of("PAID", "SHIPPED", "COMPLETED");

    private static final String STATUS_REQUESTED = "REQUESTED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final RefundRequestMapper refundRequestMapper;
    private final OrderItemMapper orderItemMapper;
    private final StoreOrderMapper storeOrderMapper;
    private final StoreMapper storeMapper;
    private final SkuInventoryMapper skuInventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;

    public RefundService(RefundRequestMapper refundRequestMapper, OrderItemMapper orderItemMapper,
            StoreOrderMapper storeOrderMapper, StoreMapper storeMapper,
            SkuInventoryMapper skuInventoryMapper, InventoryLogMapper inventoryLogMapper) {
        this.refundRequestMapper = refundRequestMapper;
        this.orderItemMapper = orderItemMapper;
        this.storeOrderMapper = storeOrderMapper;
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
        return "R" + LocalDateTime.now().format(REFUND_NO_FORMAT)
                + ThreadLocalRandom.current().nextInt(1000, 10000);
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
        patch.setStatus(STATUS_APPROVED);
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
}
