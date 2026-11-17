package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.ShipRequest;
import com.qixuan.commerce.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家端订单操作。发货以“子单”为单位 —— 一个订单三个店，三家各发各的。
 */
@RestController
@RequestMapping("/api/merchant/store-orders")
public class MerchantOrderController {

    private final ShipmentService shipmentService;

    public MerchantOrderController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping("/{id}/ship")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ship(@RequestAttribute("userId") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ShipRequest request) {
        shipmentService.ship(userId, id, request);
    }
}
