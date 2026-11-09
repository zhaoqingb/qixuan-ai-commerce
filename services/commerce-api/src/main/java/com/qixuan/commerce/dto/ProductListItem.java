package com.qixuan.commerce.dto;

import java.math.BigDecimal;

public record ProductListItem(
        Long id,
        String name,
        String categoryName,
        String storeName,
        BigDecimal minPrice,
        Long availableStock,
        Integer status) {

}
