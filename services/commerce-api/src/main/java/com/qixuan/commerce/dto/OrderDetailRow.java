package com.qixuan.commerce.dto;

import java.math.BigDecimal;

public record OrderDetailRow(
        Long orderItemId,
        Long tradeOrderId,
        String orderNo,
        String status,
        String createdAt,
        BigDecimal goodsAmount,
        BigDecimal freightAmount,
        BigDecimal discountAmount,
        BigDecimal payableAmount,
        String addressSnapshot,
        String subOrderNo,
        BigDecimal subFreightAmount,
        BigDecimal subPayableAmount,
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