package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * AI 会话。scene 区分买家(BUYER) / 商家(SELLER)，商家场景会带 store_id。
 */
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    private Long userId;

    private String scene;

    private Long storeId;

    private String title;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
