package com.qixuan.commerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CartItemUpdateRequest(

                @Min(1) Integer quantity,

                @Min(0) @Max(1) Integer selected) {
}
