package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 秒杀活动。status: DRAFT / ONLINE / OFFLINE */
@TableName("seckill_activity")
public class SeckillActivity extends BaseEntity {

    private String name;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
