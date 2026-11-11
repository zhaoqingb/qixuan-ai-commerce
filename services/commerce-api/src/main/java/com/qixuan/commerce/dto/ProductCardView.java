package com.qixuan.commerce.dto;

import java.math.BigDecimal;

public record ProductCardView(
        Long id,
        String title,
        String subtitle,
        String mainImageUrl,
        BigDecimal minPrice,
        String storeName,
        String categoryName,
        Long availableStock,
        Long soldCount) {
}
