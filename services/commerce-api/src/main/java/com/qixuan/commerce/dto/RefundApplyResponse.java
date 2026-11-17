package com.qixuan.commerce.dto;

import java.math.BigDecimal;

public record RefundApplyResponse(
        String refundNo,
        Integer quantity,
        BigDecimal refundAmount,
        String status) {
}
