package com.qixuan.commerce.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateActivityRequest(
        @NotBlank(message = "活动名称不能为空") String name,
        @NotNull(message = "开始时间不能为空") LocalDateTime startAt,
        @NotNull(message = "结束时间不能为空") LocalDateTime endAt) {

}
