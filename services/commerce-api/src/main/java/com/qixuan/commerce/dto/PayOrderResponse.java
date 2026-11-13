package com.qixuan.commerce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PayOrderResponse(
        String orderNo,
        String status,
        BigDecimal payableAmount,
        LocalDateTime paidAt) {
}
