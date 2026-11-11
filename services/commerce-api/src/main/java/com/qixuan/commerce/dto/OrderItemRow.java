package com.qixuan.commerce.dto;

import java.math.BigDecimal;

public record OrderItemRow(
        Long orderItemId,
        Long tradeOrderId,
        String orderNo,
        String status, String createdAt,
        BigDecimal goodsAmount,
        BigDecimal freightAmount,
        BigDecimal payableAmount,
        Long storeId,
        String storeName,
        Long spuId,
        Long skuId,
        String productTitle,
        String imageUrl,
        String specJson,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal itemPayableAmount) {
}
