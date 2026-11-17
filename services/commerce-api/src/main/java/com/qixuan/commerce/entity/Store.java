package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("store")
public class Store extends BaseEntity {
    private String storeName;
    private String status;
    private Long ownerUserId;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
