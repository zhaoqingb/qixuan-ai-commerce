package com.qixuan.commerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        /** 为空表示开启新会话 */
        Long conversationId,
        @NotBlank @Size(max = 2000) String message) {
}
