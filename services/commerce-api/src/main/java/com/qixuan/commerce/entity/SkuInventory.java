
package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sku_inventory")
public class SkuInventory {

    @TableId(value = "sku_id", type = IdType.INPUT)
    private Long skuId;

    private Integer availableQty;
    private Integer lockedQty;
    private Long soldQty;
    private Integer version;

    public Long getSoldQty() {
        return soldQty;
    }

    public void setSoldQty(Long soldQty) {
        this.soldQty = soldQty;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Integer getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(Integer availableQty) {
        this.availableQty = availableQty;
    }

    public Integer getLockedQty() {
        return lockedQty;
    }

    public void setLockedQty(Integer lockedQty) {
        this.lockedQty = lockedQty;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
