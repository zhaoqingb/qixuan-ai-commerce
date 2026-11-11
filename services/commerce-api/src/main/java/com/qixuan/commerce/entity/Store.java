package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("store")
public class Store extends BaseEntity {
    private String storeName;
    private String status;

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
