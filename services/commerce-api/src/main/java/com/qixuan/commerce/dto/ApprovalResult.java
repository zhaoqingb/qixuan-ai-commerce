package com.qixuan.commerce.dto;

import java.time.LocalDateTime;

/** 确认（或拒绝）之后返回给调用方的结果 */
public record ApprovalResult(
        String approvalNo,
        String status,
        String message,
        LocalDateTime executedAt) {
}
