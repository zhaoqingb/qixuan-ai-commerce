package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cart_item")
public class CartItem extends BaseEntity {

    private Long userId;
    private Long skuId;
    private Integer quantity;
    private Integer selected;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getSelected() {
        return selected;
    }

    public void setSelected(Integer selected) {
        this.selected = selected;
    }

}
