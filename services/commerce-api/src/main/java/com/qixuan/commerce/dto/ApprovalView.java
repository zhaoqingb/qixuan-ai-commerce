package com.qixuan.commerce.dto;

import java.time.LocalDateTime;

/**
 * 待确认操作的展示结构。description 是给人和模型读的一句话，
 * 例如「给 SKU 5 补货 20 件」。
 */
public record ApprovalView(
        Long id,
        String approvalNo,
        String actionCode,
        String description,
        String status,
        LocalDateTime expiresAt) {
}
