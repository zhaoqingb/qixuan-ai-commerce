package com.qixuan.commerce.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qixuan.commerce.dto.SeckillItemView;
import com.qixuan.commerce.dto.SeckillResult;
import com.qixuan.commerce.entity.SeckillItem;
import com.qixuan.commerce.entity.SeckillOrder;
import com.qixuan.commerce.entity.TradeOrder;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import com.qixuan.commerce.mapper.SeckillOrderMapper;
import com.qixuan.commerce.service.SeckillConvertService;
import com.qixuan.commerce.service.SeckillReserveService;
import com.qixuan.commerce.service.SeckillService;
import com.qixuan.commerce.service.SeckillStockService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 秒杀入口。
 *
 * 只有一个业务接口 —— 秒杀不需要购物车、不需要预览，
 * 一次请求决定抢到还是没抢到，越简单越扛得住并发。
 *
 * 另外两个是运维用的：预热库存、查 Redis 里的剩余库存。
 * 正式上线前必须收进管理员权限（和 /api/db-check 一样属于临时接口）。
 */
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private final SeckillService seckillService;
    private final SeckillOrderMapper orderMapper;
    private final SeckillConvertService convertService;

    public SeckillController(SeckillService seckillService,
            SeckillOrderMapper orderMapper,
            SeckillConvertService convertService) {
        this.seckillService = seckillService;
        this.orderMapper = orderMapper;
        this.convertService = convertService;
    }

    @PostMapping("/items/{itemId}/buy")
    public SeckillResult buy(@RequestAttribute("userId") Long userId, @PathVariable Long itemId) {
        return seckillService.buy(userId, itemId);
    }

    /** 秒杀商品列表，买家端页面用 */
    @GetMapping("/items")
    public List<SeckillItemView> list() {
        return seckillService.listOnline();
    }

    /** 取消秒杀订单，名额立即还回池子 */
    @PostMapping("/orders/{orderNo}/cancel")
    public SeckillResult cancel(@RequestAttribute("userId") Long userId,
            @PathVariable String orderNo) {
        return seckillService.cancel(userId, orderNo);
    }

    /** 当前用户的秒杀记录（含已取消） */
    @GetMapping("/orders")
    public List<SeckillOrder> myOrders(@RequestAttribute("userId") Long userId) {
        return seckillService.listMyOrders(userId);
    }

    /**
     * 把秒杀订单转成正式订单，返回正式订单号，前端拿到后跳支付页。
     *
     * 幂等：重复调用返回同一张订单，不会多建。
     */
    @PostMapping("/orders/{orderNo}/convert")
    public SeckillResult convert(@RequestAttribute("userId") Long userId,
            @PathVariable String orderNo) {
        TradeOrder trade = convertService.convert(userId, orderNo);
        return new SeckillResult(trade.getOrderNo(), trade.getStatus(), "订单已创建，请尽快支付");
    }

    @GetMapping("/orders/{orderNo}")
    public SeckillResult orderStatus(@PathVariable String orderNo) {
        SeckillOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            // 查不到有两种可能：还在队列里，或者批量落库失败了
            return new SeckillResult(orderNo, "QUEUING", "订单创建中，请稍后查询");
        }
        return new SeckillResult(order.getOrderNo(), order.getStatus(), "订单已创建");
    }

}
