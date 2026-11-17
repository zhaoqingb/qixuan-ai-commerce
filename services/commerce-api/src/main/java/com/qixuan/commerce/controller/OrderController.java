package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.CancelOrderResponse;
import com.qixuan.commerce.dto.OrderDetailRow;
import com.qixuan.commerce.dto.OrderItemRow;
import com.qixuan.commerce.dto.PayOrderResponse;
import com.qixuan.commerce.service.OrderCloseService;
import com.qixuan.commerce.service.OrderService;
import com.qixuan.commerce.service.PaymentService;
import com.qixuan.commerce.service.ShipmentService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final OrderCloseService orderCloseService;
    private final ShipmentService shipmentService;

    public OrderController(OrderService orderService, PaymentService paymentService,
            OrderCloseService orderCloseService, ShipmentService shipmentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.orderCloseService = orderCloseService;
        this.shipmentService = shipmentService;
    }

    @GetMapping
    public List<OrderItemRow> list(@RequestAttribute("userId") Long userId) {
        return orderService.list(userId);
    }

    @GetMapping("/{orderNo}")
    public List<OrderDetailRow> detail(@PathVariable String orderNo, @RequestAttribute("userId") Long userId) {
        return orderService.detail(orderNo, userId);
    }

    @PostMapping("/{orderNo}/pay")
    public PayOrderResponse pay(@PathVariable String orderNo, @RequestAttribute Long userId) {
        return paymentService.pay(orderNo, userId);
    }

    @PostMapping("/{orderNo}/cancel")
    public CancelOrderResponse cancel(@PathVariable String orderNo, @RequestAttribute("userId") Long userId) {
        return orderCloseService.cancel(orderNo, userId);
    }

    /**
     * 确认收货。按“子单”确认 —— 一个订单三个包裹，可以分开确认。
     */
    @PostMapping("/store-orders/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        shipmentService.confirm(userId, id);
    }
}
