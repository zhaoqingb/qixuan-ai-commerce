package com.qixuan.commerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增收货地址的请求体。
 *
 * isDefault 由调用方决定要不要同步取消其他地址的默认标记，
 * 但服务端有一条硬规则：用户的第一个地址无论传什么都会成为默认地址。
 */
public record AddressCreateRequest(
        @NotBlank(message = "收货人不能为空")
        @Size(max = 64, message = "收货人姓名过长")
        String receiverName,

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String receiverPhone,

        @NotBlank(message = "省份不能为空")
        @Size(max = 64, message = "省份名称过长")
        String province,

        @NotBlank(message = "城市不能为空")
        @Size(max = 64, message = "城市名称过长")
        String city,

        @NotBlank(message = "区县不能为空")
        @Size(max = 64, message = "区县名称过长")
        String district,

        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255, message = "详细地址过长")
        String detailAddress,

        boolean isDefault) {
}
