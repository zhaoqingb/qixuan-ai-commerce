package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 秒杀商品：一个活动下挂一个 SKU。
 *
 * 库存独立于 sku_inventory —— 秒杀库存卖完就结束，不影响日常销售。
 * 不变式：seckill_stock + sold_count 恒等于初始库存。
 */
@TableName("seckill_item")
public class SeckillItem extends BaseEntity {

    private Long activityId;

    private Long skuId;

    private BigDecimal seckillPrice;

    private Integer seckillStock;

    private Integer soldCount;

    private Integer perUserLimit;

    /**
     * 已从 sku_inventory 预留到本活动的件数。
     * 大于 0 表示这批货已经被活动锁住，转单时不需要（也不应该）再锁一次。
     */
    private Integer reservedQty;

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public BigDecimal getSeckillPrice() {
        return seckillPrice;
    }

    public void setSeckillPrice(BigDecimal seckillPrice) {
        this.seckillPrice = seckillPrice;
    }

    public Integer getSeckillStock() {
        return seckillStock;
    }

    public void setSeckillStock(Integer seckillStock) {
        this.seckillStock = seckillStock;
    }

    public Integer getSoldCount() {
        return soldCount;
    }

    public void setSoldCount(Integer soldCount) {
        this.soldCount = soldCount;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public Integer getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(Integer reservedQty) {
        this.reservedQty = reservedQty;
    }
}
