package com.qixuan.commerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipRequest(
        @NotBlank @Size(max = 50) String carrierCode,
        @NotBlank @Size(max = 100) String trackingNo) {
}
