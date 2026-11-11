package com.qixuan.commerce.dto;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreviewResponse(
        List<StoreGroup> storeGroups,
        BigDecimal goodsAmount,
        BigDecimal freightAmount,
        BigDecimal payableAmount,
        int totalQuantity) {

    public record StoreGroup(
            Long storeId,
            String storeName,
            BigDecimal goodsAmount,
            BigDecimal freightAmount,
            BigDecimal payableAmount,
            List<StoreItem> items) {

    }

    public record StoreItem(
            Long cartItemId,
            Long skuId,
            Long spuId,
            String productTitle,
            String imageUrl,
            String specJson,
            BigDecimal salePrice,
            Integer quantity,
            Long availableQty,
            boolean stockEnough,
            BigDecimal subtotal) {

    }
}
