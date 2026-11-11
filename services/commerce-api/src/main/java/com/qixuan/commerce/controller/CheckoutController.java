package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.CheckoutPreviewResponse;
import com.qixuan.commerce.dto.SubmitOrderRequest;
import com.qixuan.commerce.dto.SubmitOrderResponse;
import com.qixuan.commerce.entity.UserAddress;
import com.qixuan.commerce.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping("/preview")
    public CheckoutPreviewResponse preview(@RequestParam Long userId) {
        return checkoutService.preview(userId);
    }

    @GetMapping("/addresses")
    public List<UserAddress> addresses(@RequestParam Long userId) {
        return checkoutService.listAddresses(userId);
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitOrderResponse submit(@Valid @RequestBody SubmitOrderRequest request) {
        return checkoutService.submit(request);
    }
}
