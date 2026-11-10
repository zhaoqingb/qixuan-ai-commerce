package com.qixuan.commerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductStatusRequest(
        @NotNull @Positive Long storeId,
        @NotNull @Min(0) @Max(1) Integer status) {

}
