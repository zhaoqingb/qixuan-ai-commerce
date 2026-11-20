package com.qixuan.commerce.config;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.qixuan.commerce.common.RateLimiter;
import com.qixuan.commerce.controller.MallProductController;
import com.qixuan.commerce.controller.SeckillController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 把限流挂到具体接口上。
 *
 * 用 HandlerMethod 精确识别，不用字符串匹配路径 —— 路径改了不会静默失效。
 * 参数选择：
 *   商品列表 100 个/秒、桶容量 200 —— 正常浏览完全够，压测时能看出削峰
 *   秒杀下单 20 个/秒、桶容量 40   —— 秒杀本来就只有几百个名额，够了
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws IOException {

        // 只挡 HTTP 接口，静态资源/错误页放过
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        String bucket = null;
        int capacity = 0;
        int rate = 0;

        if (method.getBeanType() == MallProductController.class
                && "list".equals(method.getMethod().getName())) {
            bucket = RateLimiter.BUCKET_PRODUCT_LIST;
            // 缓存命中实测 1612 RPS，取 50% 安全系数
            capacity = 1200;
            rate = 800;
        } else if (method.getBeanType() == SeckillController.class
                && "buy".equals(method.getMethod().getName())) {
            bucket = RateLimiter.BUCKET_SECKILL_BUY;
            // 2000 件库存，目标 4 秒抢完：500/s，桶容量 1000 允许开抢瞬间洪峰
            capacity = 1000;
            rate = 500;
        }

        if (bucket == null) {
            return true;
        }

        if (rateLimiter.tryAcquire(bucket, capacity, rate)) {
            return true;
        }

        // 429 Too Many Requests，标准语义
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
        return false;
    }
}
