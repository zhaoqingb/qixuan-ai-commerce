package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单：先记录"谁抢到了"，后续再转成正式订单。
 *
 * 表里没有 updated_at，所以不继承 BaseEntity。
 * uk_seckill_user_item(user_id, item_id) 是"一人一单"的数据库兜底 ——
 * 应用层校验只能挡住正常流程，并发下要靠这个唯一索引。
 */
@TableName("seckill_order")
public class SeckillOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long activityId;

    private Long itemId;

    private Long userId;

    private Long skuId;

    private Integer quantity;

    private BigDecimal amount;

    /** PENDING / SUCCESS / FAILED */
    private String status;

    private Long tradeOrderId;

    /**
     * 1 = 有效订单，NULL = 已取消。
     *
     * 这个字段只为唯一索引服务：uk_seckill_user_item(user_id, item_id, active)。
     * MySQL 里多个 NULL 不算重复，所以取消后（active = NULL）用户还能再抢一次，
     * 同时两条有效订单仍然会冲突，防止一人多单。
     */
    private Integer active;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTradeOrderId() {
        return tradeOrderId;
    }

    public void setTradeOrderId(Long tradeOrderId) {
        this.tradeOrderId = tradeOrderId;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
