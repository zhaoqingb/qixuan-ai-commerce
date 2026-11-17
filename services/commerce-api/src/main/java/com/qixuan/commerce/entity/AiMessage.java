package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 消息。表里没有 updated_at，所以不继承 BaseEntity。
 * rich_payload 存结构化内容（工具调用、商品卡片等），普通文本消息可以为空。
 */
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** user / assistant / tool / system */
    private String messageRole;

    private String content;

    private String richPayload;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessageRole() {
        return messageRole;
    }

    public void setMessageRole(String messageRole) {
        this.messageRole = messageRole;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRichPayload() {
        return richPayload;
    }

    public void setRichPayload(String richPayload) {
        this.richPayload = richPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
