package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.qixuan.commerce.dto.SeckillSkuInfo;
import com.qixuan.commerce.entity.OrderItem;
import com.qixuan.commerce.entity.SeckillOrder;
import com.qixuan.commerce.entity.StoreOrder;
import com.qixuan.commerce.entity.TradeOrder;
import com.qixuan.commerce.entity.UserAddress;
import com.qixuan.commerce.mapper.InventoryLogMapper;
import com.qixuan.commerce.mapper.OrderItemMapper;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import com.qixuan.commerce.mapper.SeckillOrderMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;
import com.qixuan.commerce.mapper.StoreOrderMapper;
import com.qixuan.commerce.mapper.TradeOrderMapper;
import com.qixuan.commerce.mapper.UserAddressMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把秒杀订单转成正式订单（trade_order / store_order / order_item）。
 *
 * 为什么不在抢购时直接建单：
 * 抢购走的是「批量插入」，而 order_item 依赖 store_order 的自增主键，
 * 批量插入拿不到 id，只能逐条插三张表 —— fsync 会从几十次涨回几百次，
 * 3100 QPS 直接掉回 500 以内。
 *
 * 所以拆开：抢购只写 seckill_order（快），用户点「去支付」时才建单（低频，逐条插没关系）。
 */
@Service
public class SeckillConvertService {

    private static final Logger log = LoggerFactory.getLogger(SeckillConvertService.class);

    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String STATUS_SEC_KILL_SUCCESS = "SUCCESS";

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillItemMapper seckillItemMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final StoreOrderMapper storeOrderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserAddressMapper userAddressMapper;
    private final SkuInventoryMapper skuInventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final JsonMapper jsonMapper;

    public SeckillConvertService(SeckillOrderMapper seckillOrderMapper,
            SeckillItemMapper seckillItemMapper,
            TradeOrderMapper tradeOrderMapper,
            StoreOrderMapper storeOrderMapper,
            OrderItemMapper orderItemMapper,
            UserAddressMapper userAddressMapper,
            SkuInventoryMapper skuInventoryMapper,
            InventoryLogMapper inventoryLogMapper,
            JsonMapper jsonMapper) {
        this.seckillOrderMapper = seckillOrderMapper;
        this.seckillItemMapper = seckillItemMapper;
        this.tradeOrderMapper = tradeOrderMapper;
        this.storeOrderMapper = storeOrderMapper;
        this.orderItemMapper = orderItemMapper;
        this.userAddressMapper = userAddressMapper;
        this.skuInventoryMapper = skuInventoryMapper;
        this.inventoryLogMapper = inventoryLogMapper;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 转换并返回正式订单号。
     *
     * 幂等：已经转过单的直接返回原订单号，不会重复建单。
     *
     * @param userId        当前登录用户（来自 token）
     * @param seckillOrderNo 秒杀订单号
     */
    @Transactional(rollbackFor = Exception.class)
    public TradeOrder convert(Long userId, String seckillOrderNo) {

        SeckillOrder seckillOrder = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getOrderNo, seckillOrderNo)
                        .eq(SeckillOrder::getUserId, userId));
        if (seckillOrder == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "秒杀订单不存在");
        }

        // 已经转过：直接返回那张单，保证用户连点两次不会出两张订单
        if (seckillOrder.getTradeOrderId() != null) {
            TradeOrder existing = tradeOrderMapper.selectById(seckillOrder.getTradeOrderId());
            if (existing != null) {
                return existing;
            }
        }

        if (!STATUS_SEC_KILL_SUCCESS.equals(seckillOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不可下单");
        }

        UserAddress address = findDefaultAddress(userId);
        if (address == null) {
            // 注意这里不回补秒杀名额：名额已经锁定，用户补完地址还能继续下单
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "请先在结算页添加收货地址");
        }

        SeckillSkuInfo sku = seckillItemMapper.findSkuInfo(seckillOrder.getItemId());
        if (sku == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "秒杀商品信息缺失，请联系客服");
        }

        // 秒杀价由活动决定，与 sku.sale_price 无关；运费按 0 算（秒杀商品默认包邮）
        BigDecimal amount = seckillOrder.getAmount();
        String orderNo = "Q" + IdWorker.getIdStr();

        // 库存怎么扣，取决于活动有没有做过「库存预留」：
        //  - 预留过（reservedQty > 0）：这批货已经锁在 locked 里了，这里不能再锁，否则双重扣减
        //  - 没预留（= 0）：走兜底，从 available 现锁一件
        int reservedQty = sku.reservedQty() == null ? 0 : sku.reservedQty();
        if (reservedQty == 0) {
            if (skuInventoryMapper.lockStock(sku.skuId(), 1) == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "商品库存不足，暂时无法生成订单");
            }
            inventoryLogMapper.insert(sku.skuId(), orderNo, "LOCK", 1);
        }

        TradeOrder trade = new TradeOrder();
        trade.setOrderNo(orderNo);
        trade.setUserId(userId);
        // 幂等键用秒杀单号派生，同一个秒杀单永远不会生成第二张正式订单
        trade.setClientRequestId("SKC-" + seckillOrderNo);
        trade.setGoodsAmount(amount);
        trade.setDiscountAmount(BigDecimal.ZERO);
        trade.setFreightAmount(BigDecimal.ZERO);
        trade.setPayableAmount(amount);
        trade.setStatus(STATUS_PENDING_PAYMENT);
        tradeOrderMapper.insert(trade);

        StoreOrder sub = new StoreOrder();
        sub.setSubOrderNo(orderNo + "-1");
        sub.setTradeOrderId(trade.getId());
        sub.setStoreId(sku.storeId());
        sub.setUserId(userId);
        sub.setGoodsAmount(amount);
        sub.setDiscountAmount(BigDecimal.ZERO);
        sub.setFreightAmount(BigDecimal.ZERO);
        sub.setPayableAmount(amount);
        sub.setAddressSnapshot(buildAddressSnapshot(address));
        sub.setStatus(STATUS_PENDING_PAYMENT);
        storeOrderMapper.insert(sub);

        OrderItem item = new OrderItem();
        item.setStoreOrderId(sub.getId());
        item.setSpuId(sku.spuId());
        item.setSkuId(sku.skuId());
        item.setProductTitle(sku.title());
        item.setSkuSnapshot("{\"spec\":" + (sku.specJson() == null ? "null" : sku.specJson()) + "}");
        item.setImageUrlSnapshot(sku.imageUrl());
        item.setUnitPrice(amount);
        item.setQuantity(1);
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setPayableAmount(amount);
        orderItemMapper.insert(item);

        // 回填用 CAS：两个请求同时转化时，只有一个能把 trade_order_id 从 NULL 改成功，
        // 另一个 rows = 0 抛异常，整段事务回滚，不会留下两张订单。
        int rows = seckillOrderMapper.update(null, new LambdaUpdateWrapper<SeckillOrder>()
                .set(SeckillOrder::getTradeOrderId, trade.getId())
                .eq(SeckillOrder::getId, seckillOrder.getId())
                .isNull(SeckillOrder::getTradeOrderId));
        if (rows != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该秒杀订单已生成过订单");
        }

        log.info("秒杀订单转正式订单成功 seckillOrderNo={} tradeOrderNo={}", seckillOrderNo, orderNo);
        return trade;
    }

    /** 优先取默认地址，没有默认就取最新的一条 */
    private UserAddress findDefaultAddress(Long userId) {
        UserAddress address = userAddressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .last("LIMIT 1"));
        if (address != null) {
            return address;
        }
        return userAddressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getId)
                .last("LIMIT 1"));
    }

    /** 地址快照：下单那一刻的地址必须固化，用户之后改地址不影响已生成的订单 */
    private String buildAddressSnapshot(UserAddress address) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("receiverName", address.getReceiverName());
        snapshot.put("receiverPhone", address.getReceiverPhone());
        snapshot.put("province", address.getProvince());
        snapshot.put("city", address.getCity());
        snapshot.put("district", address.getDistrict());
        snapshot.put("detailAddress", address.getDetailAddress());
        try {
            return jsonMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("地址快照序列化失败", ex);
        }
    }
}
