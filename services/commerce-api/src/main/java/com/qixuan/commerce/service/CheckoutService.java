package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qixuan.commerce.dto.CheckoutItemView;
import com.qixuan.commerce.dto.CheckoutPreviewResponse;
import com.qixuan.commerce.dto.CheckoutPreviewResponse.StoreGroup;
import com.qixuan.commerce.dto.CheckoutPreviewResponse.StoreItem;
import com.qixuan.commerce.dto.SubmitOrderRequest;
import com.qixuan.commerce.dto.SubmitOrderResponse;
import com.qixuan.commerce.entity.CartItem;
import com.qixuan.commerce.entity.OrderItem;
import com.qixuan.commerce.entity.StoreOrder;
import com.qixuan.commerce.entity.TradeOrder;
import com.qixuan.commerce.entity.UserAddress;
import com.qixuan.commerce.mapper.CartItemMapper;
import com.qixuan.commerce.mapper.OrderItemMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;
import com.qixuan.commerce.mapper.StoreOrderMapper;
import com.qixuan.commerce.mapper.TradeOrderMapper;
import com.qixuan.commerce.mapper.UserAddressMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class CheckoutService {

    private static final BigDecimal FREE_FREIGHT_THRESHOLD = new BigDecimal("99.00");
    private static final BigDecimal FREIGHT_FEE = new BigDecimal("10.00");
    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";

    private final CartItemMapper cartItemMapper;
    private final UserAddressMapper userAddressMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final StoreOrderMapper storeOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SkuInventoryMapper skuInventoryMapper;

    public CheckoutService(CartItemMapper cartItemMapper,
            UserAddressMapper userAddressMapper,
            TradeOrderMapper tradeOrderMapper,
            StoreOrderMapper storeOrderMapper,
            OrderItemMapper orderItemMapper,
            SkuInventoryMapper skuInventoryMapper) {
        this.cartItemMapper = cartItemMapper;
        this.userAddressMapper = userAddressMapper;
        this.tradeOrderMapper = tradeOrderMapper;
        this.storeOrderMapper = storeOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.skuInventoryMapper = skuInventoryMapper;
    }

    public List<UserAddress> listAddresses(Long userId) {
        return userAddressMapper.selectList(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getId));
    }

    public CheckoutPreviewResponse preview(Long userId) {
        List<CheckoutItemView> rows = cartItemMapper.findSelectedForCheckout(userId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "购物车没有已选商品");
        }

        List<StoreGroup> groups = groupAndPrice(rows);

        BigDecimal goods = BigDecimal.ZERO;
        BigDecimal freight = BigDecimal.ZERO;
        int quantity = 0;
        for (StoreGroup group : groups) {
            goods = goods.add(group.goodsAmount());
            freight = freight.add(group.freightAmount());
            quantity += sumQuantity(group);
        }

        return new CheckoutPreviewResponse(
                groups, goods, freight, goods.add(freight), quantity);
    }

    @Transactional(rollbackFor = Exception.class)
    public SubmitOrderResponse submit(SubmitOrderRequest request) {
        TradeOrder existing = tradeOrderMapper.selectOne(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getUserId, request.userId())
                        .eq(TradeOrder::getClientRequestId, request.clientRequestId()));
        if (existing != null) {
            long subCount = storeOrderMapper.selectCount(
                    new LambdaQueryWrapper<StoreOrder>()
                            .eq(StoreOrder::getTradeOrderId, existing.getId()));
            return new SubmitOrderResponse(
                    existing.getId(),
                    existing.getOrderNo(),
                    existing.getPayableAmount(),
                    existing.getStatus(),
                    subCount,
                    orderItemMapper.countQuantityByTradeOrder(existing.getId()),
                    true);
        }

        UserAddress address = userAddressMapper.selectOne(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getId, request.addressId())
                        .eq(UserAddress::getUserId, request.userId()));
        if (address == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "收货地址不存在");
        }

        List<CheckoutItemView> rows = cartItemMapper.findSelectedForCheckout(request.userId());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "购物车没有已选商品");
        }

        List<StoreGroup> groups = groupAndPrice(rows);
        for (StoreGroup group : groups) {
            for (StoreItem item : group.items()) {
                if (!item.stockEnough()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "商品库存不足：" + item.productTitle());
                }
            }
        }

        BigDecimal goodsAmount = BigDecimal.ZERO;
        BigDecimal freightAmount = BigDecimal.ZERO;
        for (StoreGroup group : groups) {
            goodsAmount = goodsAmount.add(group.goodsAmount());
            freightAmount = freightAmount.add(group.freightAmount());
        }

        String orderNo = generateOrderNo();

        TradeOrder trade = new TradeOrder();
        trade.setOrderNo(orderNo);
        trade.setUserId(request.userId());
        trade.setClientRequestId(request.clientRequestId());
        trade.setGoodsAmount(goodsAmount);
        trade.setDiscountAmount(BigDecimal.ZERO);
        trade.setFreightAmount(freightAmount);
        trade.setPayableAmount(goodsAmount.add(freightAmount));
        trade.setStatus(STATUS_PENDING_PAYMENT);
        tradeOrderMapper.insert(trade);

        String addressSnapshot = "{"
                + "\"receiverName\":" + jsonString(address.getReceiverName()) + ","
                + "\"receiverPhone\":" + jsonString(address.getReceiverPhone()) + ","
                + "\"province\":" + jsonString(address.getProvince()) + ","
                + "\"city\":" + jsonString(address.getCity()) + ","
                + "\"district\":" + jsonString(address.getDistrict()) + ","
                + "\"detailAddress\":" + jsonString(address.getDetailAddress())
                + "}";

        int subIndex = 0;
        int itemCount = 0;

        for (StoreGroup group : groups) {
            subIndex++;

            StoreOrder sub = new StoreOrder();
            sub.setSubOrderNo(orderNo + "-" + subIndex);
            sub.setTradeOrderId(trade.getId());
            sub.setStoreId(group.storeId());
            sub.setUserId(request.userId());
            sub.setGoodsAmount(group.goodsAmount());
            sub.setDiscountAmount(BigDecimal.ZERO);
            sub.setFreightAmount(group.freightAmount());
            sub.setPayableAmount(group.payableAmount());
            sub.setAddressSnapshot(addressSnapshot);
            sub.setStatus(STATUS_PENDING_PAYMENT);
            storeOrderMapper.insert(sub);

            for (StoreItem item : group.items()) {
                if (skuInventoryMapper.lockStock(item.skuId(), item.quantity()) == 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "商品库存不足：" + item.productTitle());
                }

                OrderItem orderItem = new OrderItem();
                orderItem.setStoreOrderId(sub.getId());
                orderItem.setSpuId(item.spuId());
                orderItem.setSkuId(item.skuId());
                orderItem.setProductTitle(item.productTitle());
                orderItem.setSkuSnapshot(
                        "{\"spec\":" + (item.specJson() == null ? "null" : item.specJson()) + "}");
                orderItem.setImageUrlSnapshot(item.imageUrl());
                orderItem.setUnitPrice(item.salePrice());
                orderItem.setQuantity(item.quantity());
                orderItem.setDiscountAmount(BigDecimal.ZERO);
                orderItem.setPayableAmount(item.subtotal());
                orderItemMapper.insert(orderItem);

                itemCount += item.quantity();
            }
        }

        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, request.userId())
                .eq(CartItem::getSelected, 1));

        return new SubmitOrderResponse(
                trade.getId(),
                trade.getOrderNo(),
                trade.getPayableAmount(),
                trade.getStatus(),
                groups.size(),
                itemCount,
                false);
    }

    private List<StoreGroup> groupAndPrice(List<CheckoutItemView> rows) {
        Map<Long, List<CheckoutItemView>> byStore = rows.stream()
                .collect(Collectors.groupingBy(
                        CheckoutItemView::storeId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<StoreGroup> groups = new ArrayList<>();
        for (Map.Entry<Long, List<CheckoutItemView>> entry : byStore.entrySet()) {
            List<CheckoutItemView> storeRows = entry.getValue();

            BigDecimal storeGoods = BigDecimal.ZERO;
            List<StoreItem> items = new ArrayList<>();

            for (CheckoutItemView row : storeRows) {
                BigDecimal subtotal = row.salePrice()
                        .multiply(BigDecimal.valueOf(row.quantity()));
                items.add(new StoreItem(
                        row.cartItemId(),
                        row.skuId(),
                        row.spuId(),
                        row.productTitle(),
                        row.imageUrl(),
                        row.specJson(),
                        row.salePrice(),
                        row.quantity(),
                        row.availableQty(),
                        row.availableQty() >= row.quantity(),
                        subtotal));
                storeGoods = storeGoods.add(subtotal);
            }

            BigDecimal freight = storeGoods.compareTo(FREE_FREIGHT_THRESHOLD) >= 0
                    ? BigDecimal.ZERO
                    : FREIGHT_FEE;

            groups.add(new StoreGroup(
                    entry.getKey(),
                    storeRows.get(0).storeName(),
                    storeGoods,
                    freight,
                    storeGoods.add(freight),
                    items));
        }
        return groups;
    }

    private int sumQuantity(StoreGroup group) {
        int total = 0;
        for (StoreItem item : group.items()) {
            total += item.quantity();
        }
        return total;
    }

    private String generateOrderNo() {
        return "Q" + LocalDateTime.now().format(ORDER_NO_FORMAT)
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
