package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 物流单。注意 shipment 表没有 created_at / updated_at，只有 shipped_at，
 * 所以不能继承 BaseEntity（否则会去映射不存在的列）。
 */
@TableName("shipment")
public class Shipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 一个子单只能有一个物流单（表上有唯一索引） */
    private Long storeOrderId;

    private String carrierCode;

    private String trackingNo;

    private String status;

    private LocalDateTime shippedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStoreOrderId() {
        return storeOrderId;
    }

    public void setStoreOrderId(Long storeOrderId) {
        this.storeOrderId = storeOrderId;
    }

    public String getCarrierCode() {
        return carrierCode;
    }

    public void setCarrierCode(String carrierCode) {
        this.carrierCode = carrierCode;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }
}
