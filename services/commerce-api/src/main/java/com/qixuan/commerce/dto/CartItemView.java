package com.qixuan.commerce.dto;

import java.math.BigDecimal;

public record CartItemView(
        Long cartItemId,
        Long skuId,
        Integer quantity,
        Integer selected,
        String skuCode,
        String specJson,
        BigDecimal salePrice,
        String productTitle,
        String imageUrl,
        Long availableQty) {
}