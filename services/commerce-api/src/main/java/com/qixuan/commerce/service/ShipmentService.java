package com.qixuan.commerce.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qixuan.commerce.dto.ShipRequest;
import com.qixuan.commerce.entity.Shipment;
import com.qixuan.commerce.entity.Store;
import com.qixuan.commerce.entity.StoreOrder;
import com.qixuan.commerce.entity.TradeOrder;
import com.qixuan.commerce.mapper.ShipmentMapper;
import com.qixuan.commerce.mapper.StoreMapper;
import com.qixuan.commerce.mapper.StoreOrderMapper;
import com.qixuan.commerce.mapper.TradeOrderMapper;

@Service
public class ShipmentService {

    static final String STATUS_PAID = "PAID";
    static final String STATUS_SHIPPED = "SHIPPED";
    static final String STATUS_COMPLETED = "COMPLETED";

    private final StoreOrderMapper storeOrderMapper;
    private final StoreMapper storeMapper;
    private final ShipmentMapper shipmentMapper;
    private final TradeOrderMapper tradeOrderMapper;

    public ShipmentService(StoreOrderMapper storeOrderMapper, StoreMapper storeMapper,
            ShipmentMapper shipmentMapper, TradeOrderMapper tradeOrderMapper) {
        this.storeOrderMapper = storeOrderMapper;
        this.storeMapper = storeMapper;
        this.shipmentMapper = shipmentMapper;
        this.tradeOrderMapper = tradeOrderMapper;
    }

    private static int rank(String status) {
        return switch (status) {
            case "PENDING_PAYMENT" -> 0;
            case STATUS_PAID -> 1;
            case STATUS_SHIPPED -> 2;
            case STATUS_COMPLETED -> 3;
            default -> -1;
        };
    }

    @Transactional
    public void ship(Long userId, Long storeOrderId, ShipRequest request) {

        StoreOrder sub = storeOrderMapper.selectById(storeOrderId);
        if (sub == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "子单不存在");
        }
        Store store = storeMapper.selectById(sub.getStoreId());
        if (store == null || !userId.equals(store.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "子单不存在");
        }
        StoreOrder patch = new StoreOrder();
        patch.setStatus(STATUS_SHIPPED);

        int rows = storeOrderMapper.update(patch, new LambdaUpdateWrapper<StoreOrder>()
                .eq(StoreOrder::getId, storeOrderId).eq(StoreOrder::getStatus, STATUS_PAID));
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前订单不可发货");
        }

        Shipment shipment = new Shipment();
        shipment.setStoreOrderId(storeOrderId);
        shipment.setCarrierCode(request.carrierCode());
        shipment.setTrackingNo(request.trackingNo());
        shipment.setStatus(STATUS_SHIPPED);
        shipmentMapper.insert(shipment);

        syncTradeStatus(sub.getTradeOrderId());
    }

    @Transactional
    public void confirm(Long userId, Long storeOrderId) {
        StoreOrder sub = storeOrderMapper.selectById(storeOrderId);
        if (sub == null || !sub.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "子单不存在");
        }
        StoreOrder patch = new StoreOrder();
        patch.setStatus(STATUS_COMPLETED);
        int rows = storeOrderMapper.update(patch, new LambdaUpdateWrapper<StoreOrder>()
                .eq(StoreOrder::getId, storeOrderId).eq(StoreOrder::getStatus, STATUS_SHIPPED));
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前订单不可确认收货");
        }
        syncTradeStatus(sub.getTradeOrderId());
    }

    /**
     * 父单状态联动：所有子单都到了某个状态，父单才跟着变。
     * 只有子单状态变化是“一个个来”的时候才需要它 —— 支付是同步改所有子单，所以不需要。
     */
    private void syncTradeStatus(Long tradeOrderId) {

        List<StoreOrder> subs = storeOrderMapper
                .selectList(new LambdaQueryWrapper<StoreOrder>().eq(StoreOrder::getTradeOrderId, tradeOrderId));
        if (subs.isEmpty()) {
            return;
        }
        String lowest = STATUS_COMPLETED;
        for (StoreOrder order : subs) {
            if (rank(order.getStatus()) < rank(lowest)) {
                lowest = order.getStatus();
            }
        }
        TradeOrder trade = tradeOrderMapper.selectById(tradeOrderId);
        if (trade == null || rank(trade.getStatus()) >= rank(lowest)) {
            return;
        }
        TradeOrder patch = new TradeOrder();
        patch.setStatus(lowest);
        tradeOrderMapper.update(patch, new LambdaUpdateWrapper<TradeOrder>().eq(TradeOrder::getId, tradeOrderId)
                .eq(TradeOrder::getStatus, trade.getStatus()));
    }
}
