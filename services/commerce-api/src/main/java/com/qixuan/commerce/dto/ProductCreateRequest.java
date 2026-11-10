package com.qixuan.commerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductCreateRequest(

        @NotNull @Positive Long storeId,

        @NotNull @Positive Long categoryId,

        @NotBlank @Size(max = 200) String name,

        @NotEmpty List<@Valid SkuItem> skus,
        String description) {
    public record SkuItem(
            @NotBlank @Size(max = 64) String skuCode,

            @NotEmpty Map<@NotBlank String, @NotBlank String> specs,

            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,

            @NotNull @Min(0) Integer stock) {
    }
}
