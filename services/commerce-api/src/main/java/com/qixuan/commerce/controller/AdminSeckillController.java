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
import com.qixuan.commerce.service.AdminSeckillService;

import jakarta.validation.Valid;

import com.qixuan.commerce.dto.ActivityCheckIssue;
import com.qixuan.commerce.dto.CreateActivityRequest;
import com.qixuan.commerce.entity.SeckillActivity;

@RestController
@RequestMapping("/api/admin/seckill")
public class AdminSeckillController {
    private final AdminSeckillService adminSeckillService;

    public AdminSeckillController(AdminSeckillService adminSeckillService) {
        this.adminSeckillService = adminSeckillService;
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

    @PostMapping("/activities/{id}/online")
    public SeckillActivity online(@PathVariable Long id) {
        return adminSeckillService.online(id);
    }

    @GetMapping("/activities/{id}/precheck")
    public List<ActivityCheckIssue> precheck(@PathVariable Long id) {
        return adminSeckillService.precheck(id);
    }

    @PostMapping("/activities/{id}/offline")
    public SeckillActivity offline(@PathVariable Long id) {
        return adminSeckillService.offline(id);
    }

}
