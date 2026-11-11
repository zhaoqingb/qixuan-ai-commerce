package com.qixuan.commerce.service;

import com.qixuan.commerce.dto.OrderDetailRow;
import com.qixuan.commerce.dto.OrderItemRow;
import com.qixuan.commerce.mapper.OrderItemMapper;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderItemMapper orderItemMapper;

    public OrderService(OrderItemMapper orderItemMapper) {
        this.orderItemMapper = orderItemMapper;
    }

    public List<OrderItemRow> list(Long userId) {
        return orderItemMapper.findByUserId(userId);
    }

    public List<OrderDetailRow> detail(String orderNo, Long userId) {
        return orderItemMapper.findDetailByOrderNo(orderNo, userId);
    }
}
