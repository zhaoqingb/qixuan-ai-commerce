package com.qixuan.commerce.dto;

/** 秒杀结果：抢到了返回订单号，没抢到返回原因 */
public record SeckillResult(String orderNo, String status, String message) {
}
