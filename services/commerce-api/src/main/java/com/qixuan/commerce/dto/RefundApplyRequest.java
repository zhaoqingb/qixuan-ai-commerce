package com.qixuan.commerce.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RefundApplyRequest(
        @NotNull @Positive Long orderItemId,
        @NotNull @Min(1) Integer quantity,
        @Size(max = 500) String reason) {
}
