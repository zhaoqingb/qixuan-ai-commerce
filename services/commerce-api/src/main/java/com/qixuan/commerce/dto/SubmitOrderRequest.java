package com.qixuan.commerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitOrderRequest(
        @NotNull Long userId,
        @NotNull Long addressId,
        @NotBlank @Size(max = 64) String clientRequestId) {
}
