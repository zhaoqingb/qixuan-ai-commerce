package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qixuan.commerce.entity.SeckillItem;
import com.qixuan.commerce.mapper.InventoryLogMapper;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 秒杀活动的库存预留与释放。
 *
 * 为什么需要：秒杀池（seckill_item.seckill_stock）和 sku 可用库存（sku_inventory.available_qty）
 * 是两本账。如果活动配了 500 件而 sku 只有 138 件可用，
 * 前 136 单能正常转单，第 137 单会在「用户点支付」时失败 —— 那时候用户已经抢到了。
 *
 * 正确做法：活动上线前把这批货从 available 划到 locked（预留），
 * 让「库存不够」在配置阶段就暴露出来，而不是在用户付款时。
 */
@Service
public class SeckillReserveService {

    private static final Logger log = LoggerFactory.getLogger(SeckillReserveService.class);

    private final SeckillItemMapper itemMapper;
    private final SkuInventoryMapper skuInventoryMapper;
    private final InventoryLogMapper inventoryLogMapper;

    public SeckillReserveService(SeckillItemMapper itemMapper,
            SkuInventoryMapper skuInventoryMapper,
            InventoryLogMapper inventoryLogMapper) {
        this.itemMapper = itemMapper;
        this.skuInventoryMapper = skuInventoryMapper;
        this.inventoryLogMapper = inventoryLogMapper;
    }

    /**
     * 预留库存：把这个活动当前剩余的秒杀库存，整体从 sku 可用库存划到锁定。
     *
     * 幂等靠 CAS：只有 reserved_qty 还是 0 的时候才更新成功，
     * 重复调用会在最后一步返回 0 行，整段事务回滚。
     *
     * @return 本次预留的件数
     */
    @Transactional(rollbackFor = Exception.class)
    public int reserve(Long itemId) {
        SeckillItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "秒杀商品不存在");
        }
        if (item.getReservedQty() != null && item.getReservedQty() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该商品已预留过库存");
        }

        int qty = item.getSeckillStock();
        if (qty <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可预留的库存");
        }

        // 注意：这里必须用条件更新，不能先查 available 再判断 —— 并发下会重复预留
        if (skuInventoryMapper.lockStock(item.getSkuId(), qty) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "SKU 可用库存不足，无法预留 " + qty + " 件，请先补货");
        }
        // 业务号必须带上时间：同一个活动可以反复上下线，
        // 而 inventory_log 有 UNIQUE(sku_id, business_no, operation)，
        // 用固定业务号会导致第二次上线撞唯一键。
        inventoryLogMapper.insert(item.getSkuId(),
                "SK-RESERVE-" + itemId + "-" + System.currentTimeMillis(), "LOCK", qty);

        int rows = itemMapper.update(null, new LambdaUpdateWrapper<SeckillItem>()
                .set(SeckillItem::getReservedQty, qty)
                .eq(SeckillItem::getId, itemId)
                .eq(SeckillItem::getReservedQty, 0));
        if (rows != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该商品已预留过库存");
        }

        log.info("秒杀库存已预留 itemId={} skuId={} qty={}", itemId, item.getSkuId(), qty);
        return qty;
    }

    /**
     * 活动结束后释放：把没卖出去的部分从 locked 还回 available。
     *
     * 只处理「还没被抢」的那部分（= 当前 seckill_stock）。
     * 已经被抢但还没付款的，走订单超时关闭流程，不在这个方法里管。
     */
    @Transactional(rollbackFor = Exception.class)
    public int release(Long itemId) {
        SeckillItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "秒杀商品不存在");
        }
        int reserved = item.getReservedQty() == null ? 0 : item.getReservedQty();
        if (reserved <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该商品没有预留记录");
        }

        // 释放量取「还没卖出去的」和「预留总量」的较小值，防止账已漂时算负数
        int toRelease = Math.min(item.getSeckillStock(), reserved);
        if (toRelease > 0) {
            if (skuInventoryMapper.releaseStock(item.getSkuId(), toRelease) == 0) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "锁定数量不足，无法释放，需要人工对账");
            }
            inventoryLogMapper.insert(item.getSkuId(),
                    "SK-RELEASE-" + itemId + "-" + System.currentTimeMillis(), "RELEASE", toRelease);
        }

        itemMapper.update(null, new LambdaUpdateWrapper<SeckillItem>()
                .set(SeckillItem::getReservedQty, 0)
                .eq(SeckillItem::getId, itemId));

        log.info("秒杀库存已释放 itemId={} skuId={} qty={}", itemId, item.getSkuId(), toRelease);
        return toRelease;
    }
}
