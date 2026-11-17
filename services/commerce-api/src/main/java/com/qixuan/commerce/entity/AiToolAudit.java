package com.qixuan.commerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 工具调用审计：Agent 每一次"动手"都留痕。
 *
 * 只读和写操作都记，因为排查问题时最想知道的往往是
 * "它当时到底查了什么、看到了什么"。
 */
@TableName("ai_tool_audit")
public class AiToolAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long actorUserId;

    private Long storeId;

    /** 如果是审批后执行的，记下对应的审批单 id */
    private Long approvalId;

    private String toolName;

    /** 请求摘要（截断到 1000 字符，避免爆列） */
    private String requestSummary;

    /** 结果摘要（截断到 1000 字符） */
    private String resultSummary;

    /** 1 成功 / 0 失败 */
    private Integer success;

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

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getRequestSummary() {
        return requestSummary;
    }

    public void setRequestSummary(String requestSummary) {
        this.requestSummary = requestSummary;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
