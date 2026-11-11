package com.qixuan.commerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long skuId,
        @NotNull @Min(1) Integer quantity) {
}
