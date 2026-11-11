package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.OrderDetailRow;
import com.qixuan.commerce.dto.OrderItemRow;
import com.qixuan.commerce.service.OrderService;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public List<OrderItemRow> list(@RequestParam Long userId) {
        return orderService.list(userId);
    }

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{orderNo}")
    public List<OrderDetailRow> detail(@PathVariable String orderNo, @RequestParam Long userId) {
        return orderService.detail(orderNo, userId);
    }

}
