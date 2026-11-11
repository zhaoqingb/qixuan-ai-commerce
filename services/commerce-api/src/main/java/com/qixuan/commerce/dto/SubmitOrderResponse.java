package com.qixuan.commerce.dto;

import java.math.BigDecimal;

public record SubmitOrderResponse(
        Long tradeOrderId,
        String orderNo,
        BigDecimal payableAmount,
        String status,
        long storeOrderCount,
        int itemCount,
        boolean duplicated) {
}
