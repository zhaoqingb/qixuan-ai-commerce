package com.qixuan.commerce.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.qixuan.commerce.dto.ActivityCheckIssue;
import com.qixuan.commerce.dto.CreateActivityRequest;
import com.qixuan.commerce.entity.SeckillActivity;
import com.qixuan.commerce.entity.SeckillItem;
import com.qixuan.commerce.dto.SeckillResult;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import com.qixuan.commerce.service.AdminSeckillService;
import com.qixuan.commerce.service.SeckillReserveService;
import com.qixuan.commerce.service.SeckillStockService;

import jakarta.validation.Valid;

/**
 * 秒杀活动管理端。
 *
 * 所有接口都在 /api/admin/** 下，由 AdminInterceptor 强制校验 ADMIN 角色。
 * 原来的 reserve / release / sync / stock 放在 /api/seckill/** 下，只过了登录校验，
 * 任何登录用户都能重置 Redis 库存 —— 收进来之后才是真正的运维接口。
 */
@RestController
@RequestMapping("/api/admin/seckill")
public class AdminSeckillController {

    private final AdminSeckillService adminSeckillService;
    private final SeckillReserveService reserveService;
    private final SeckillStockService stockService;
    private final SeckillItemMapper itemMapper;

    public AdminSeckillController(AdminSeckillService adminSeckillService,
            SeckillReserveService reserveService,
            SeckillStockService stockService,
            SeckillItemMapper itemMapper) {
        this.adminSeckillService = adminSeckillService;
        this.reserveService = reserveService;
        this.stockService = stockService;
        this.itemMapper = itemMapper;
    }

    @GetMapping("/activities")
    public List<SeckillActivity> activities() {
        return adminSeckillService.listActivities();
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public SeckillActivity create(@Valid @RequestBody CreateActivityRequest request) {
        return adminSeckillService.createActivity(request);
    }

    @GetMapping("/activities/{id}/precheck")
    public List<ActivityCheckIssue> precheck(@PathVariable Long id) {
        return adminSeckillService.precheck(id);
    }

    @PostMapping("/activities/{id}/online")
    public SeckillActivity online(@PathVariable Long id) {
        return adminSeckillService.online(id);
    }

    @PostMapping("/activities/{id}/offline")
    public SeckillActivity offline(@PathVariable Long id) {
        return adminSeckillService.offline(id);
    }

    /** 手动预留库存 */
    @PostMapping("/items/{itemId}/reserve")
    public SeckillResult reserve(@PathVariable Long itemId) {
        int qty = reserveService.reserve(itemId);
        return new SeckillResult(null, "RESERVED", "已预留 " + qty + " 件");
    }

    /** 释放没卖完的库存 */
    @PostMapping("/items/{itemId}/release")
    public SeckillResult release(@PathVariable Long itemId) {
        int qty = reserveService.release(itemId);
        return new SeckillResult(null, "RELEASED", "已释放 " + qty + " 件");
    }

    /** 把数据库库存灌进 Redis，压测前调一次 */
    @PostMapping("/items/{itemId}/stock/sync")
    public SeckillResult syncStock(@PathVariable Long itemId) {
        SeckillItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "秒杀商品不存在");
        }
        boolean loaded = stockService.loadStock(itemId, item.getSeckillStock());
        if (loaded) {
            return new SeckillResult(null, "SYNC", "已预热 " + item.getSeckillStock());
        }
        Integer remain = stockService.currentStock(itemId);
        return new SeckillResult(null, "SYNC",
                "已存在，跳过（当前 " + (remain == null ? "未知" : remain) + "）");
    }

    /** 看 Redis 里还剩多少 */
    @GetMapping("/items/{itemId}/stock")
    public SeckillResult stock(@PathVariable Long itemId) {
        Integer stock = stockService.currentStock(itemId);
        return new SeckillResult(null, "STOCK", stock == null ? "未预热" : String.valueOf(stock));
    }
}
