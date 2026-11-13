package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.CancelOrderResponse;
import com.qixuan.commerce.dto.OrderDetailRow;
import com.qixuan.commerce.dto.OrderItemRow;
import com.qixuan.commerce.dto.PayOrderResponse;
import com.qixuan.commerce.service.OrderCloseService;
import com.qixuan.commerce.service.OrderService;
import com.qixuan.commerce.service.PaymentService;

import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final OrderCloseService orderCloseService;

    public OrderController(OrderService orderService, PaymentService paymentService,
            OrderCloseService orderCloseService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.orderCloseService = orderCloseService;
    }

    @GetMapping
    public List<OrderItemRow> list(@RequestParam Long userId) {
        return orderService.list(userId);
    }

    @GetMapping("/{orderNo}")
    public List<OrderDetailRow> detail(@PathVariable String orderNo, @RequestParam Long userId) {
        return orderService.detail(orderNo, userId);
    }

    @PostMapping("/{orderNo}/pay")
    public PayOrderResponse pay(@PathVariable String orderNo, @RequestParam Long userId) {
        return paymentService.pay(orderNo, userId);
    }

    @PostMapping("/{orderNo}/cancel")
    public CancelOrderResponse cancel(@PathVariable String orderNo, @RequestParam Long userId) {
        return orderCloseService.cancel(orderNo, userId);
    }
}
