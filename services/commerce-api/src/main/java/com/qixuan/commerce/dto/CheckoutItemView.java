package com.qixuan.commerce.dto;

import java.math.BigDecimal;

public record CheckoutItemView(
        Long cartItemId,
        Long skuId,

        Long spuId,

        Long storeId,

        String storeName,

        String productTitle,

        String imageUrl,

        String specJson,
        BigDecimal salePrice,
        Integer quantity,

        Long availableQty) {
}
