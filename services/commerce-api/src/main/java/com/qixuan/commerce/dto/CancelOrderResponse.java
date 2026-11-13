package com.qixuan.commerce.dto;

import java.time.LocalDateTime;

public record CancelOrderResponse(
        String orderNo,
        String status,
        LocalDateTime closedAt) {
}
