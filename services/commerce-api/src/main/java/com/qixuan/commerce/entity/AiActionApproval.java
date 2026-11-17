package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 写操作的审批单（人在环 / Human-in-the-loop）。
 *
 * 模型永远不能直接改业务数据：它只能"提议"，写成一条 PENDING 记录，
 * 由真人点确认后，系统才用这张单子里固化的参数去执行。
 *
 * 表里没有 updated_at，所以不继承 BaseEntity（那会去写不存在的列）。
 */
@TableName("ai_action_approval")
public class AiActionApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String approvalNo;

    private Long conversationId;

    private Long actorUserId;

    private Long storeId;

    /** 动作代码，例如 RESTOCK */
    private String actionCode;

    /** 执行该动作所需的完整参数，JSON 文本（审批通过后按它执行，不信任新传入的参数） */
    private String requestPayload;

    private String status;

    /** 过期时间：超时不执行，防止"三天前点的确认"被翻出来用 */
    private LocalDateTime expiresAt;

    private LocalDateTime decidedAt;

    private LocalDateTime executedAt;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApprovalNo() {
        return approvalNo;
    }

    public void setApprovalNo(String approvalNo) {
        this.approvalNo = approvalNo;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
