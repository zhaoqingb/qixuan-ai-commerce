package com.qixuan.commerce.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String title,
        String subtitle,
        String mainImageUrl,
        String description,
        Long storeId,
        String storeName,
        Long categoryId,
        List<SkuView> skus) {

    public record SkuView(
            long skuId,
            String skuCode,
            String specJson,
            BigDecimal salePrice,
            BigDecimal listPrice,
            Integer availableQty) {

    }
}
