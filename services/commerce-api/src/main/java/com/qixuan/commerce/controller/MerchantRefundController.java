package com.qixuan.commerce.controller;

import com.qixuan.commerce.service.RefundService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家端售后接口。和买家端的 /api/refunds 是两个入口、两种权限。
 */
@RestController
@RequestMapping("/api/merchant/refunds")
public class MerchantRefundController {

    private final RefundService refundService;

    public MerchantRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        refundService.approve(userId, id);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        refundService.reject(userId, id);
    }
}
