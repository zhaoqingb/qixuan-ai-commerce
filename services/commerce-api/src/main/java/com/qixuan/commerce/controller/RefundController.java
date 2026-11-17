package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.RefundApplyRequest;
import com.qixuan.commerce.dto.RefundApplyResponse;
import com.qixuan.commerce.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家端售后接口。
 */
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundApplyResponse apply(@RequestAttribute("userId") Long userId,
            @Valid @RequestBody RefundApplyRequest request) {
        return refundService.apply(userId, request);
    }
}
