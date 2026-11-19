package com.qixuan.commerce.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qixuan.commerce.dto.ActivityCheckIssue;
import com.qixuan.commerce.dto.CreateActivityRequest;
import com.qixuan.commerce.entity.SeckillActivity;
import com.qixuan.commerce.entity.SeckillItem;
import com.qixuan.commerce.entity.SkuInventory;
import com.qixuan.commerce.mapper.SeckillActivityMapper;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import com.qixuan.commerce.mapper.SkuInventoryMapper;

@Service
public class AdminSeckillService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ONLINE = "ONLINE";
    private static final String STATUS_OFFLINE = "OFFLINE";
    private final SeckillActivityMapper activityMapper;
    private final SeckillItemMapper itemMapper;
    private final SeckillReserveService reserveService;
    private final SkuInventoryMapper skuInventoryMapper;

    public AdminSeckillService(SeckillActivityMapper activityMapper, SeckillItemMapper itemMapper,
            SeckillReserveService reserveService, SkuInventoryMapper skuInventoryMapper) {
        this.activityMapper = activityMapper;
        this.itemMapper = itemMapper;
        this.reserveService = reserveService;
        this.skuInventoryMapper = skuInventoryMapper;
    }

    public List<SeckillActivity> listActivities() {
        return activityMapper
                .selectList(new LambdaQueryWrapper<SeckillActivity>().orderByDesc(SeckillActivity::getStartAt));

    }

    public SeckillActivity createActivity(CreateActivityRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (request.startAt().isAfter(request.endAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始时间不能晚于结束时间");
        }

        SeckillActivity activity = new SeckillActivity();
        activity.setName(request.name());
        activity.setStartAt(request.startAt());
        activity.setEndAt(request.endAt());
        activity.setStatus("DRAFT");
        activityMapper.insert(activity);
        return activity;
    }

    @Transactional(rollbackFor = Exception.class)
    public SeckillActivity online(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "活动不存在");
        }
        if (!STATUS_DRAFT.equals(activity.getStatus()) && !STATUS_OFFLINE.equals(activity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不可上线");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "活动开始时间太近，需至少提前5分钟上线");
        }
        if (activity.getEndAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "活动已过结束时间");
        }
        List<SeckillItem> items = itemMapper
                .selectList(new LambdaQueryWrapper<SeckillItem>()
                        .eq(SeckillItem::getActivityId, activityId));
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "活动还没有配置商品，无法上线");
        }
        for (SeckillItem item : items) {
            reserveService.reserve(item.getId());
        }
        activity.setStatus(STATUS_ONLINE);
        activityMapper.updateById(activity);
        return activity;
    }

    public List<ActivityCheckIssue> precheck(Long activityId) {
        List<ActivityCheckIssue> issues = new ArrayList<>();

        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "活动不存在");
        }
        String status = activity.getStatus();
        if (!STATUS_DRAFT.equals(status) && !STATUS_OFFLINE.equals(status)) {
            issues.add(new ActivityCheckIssue("STATUS", "当前状态不可上线", null));
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartAt().isBefore(now.plusMinutes(5))) {
            issues.add(new ActivityCheckIssue("START_AT", "活动开始时间太近，需至少提前5分钟上线", null));
        }
        if (activity.getEndAt().isBefore(now)) {
            issues.add(new ActivityCheckIssue("END_AT", "活动已过结束时间", null));
        }
        List<SeckillItem> items = itemMapper
                .selectList(new LambdaQueryWrapper<SeckillItem>().eq(SeckillItem::getActivityId, activityId)
                        .orderByAsc(SeckillItem::getId));
        if (items.isEmpty()) {
            issues.add(new ActivityCheckIssue("ITEMS", "活动还没有配置商品，无法上线", null));
            return issues;
        }
        for (SeckillItem item : items) {
            SkuInventory inv = skuInventoryMapper.selectById(item.getSkuId());
            int available = inv == null ? 0 : inv.getAvailableQty();
            if (available < item.getSeckillStock()) {
                issues.add(new ActivityCheckIssue("INVENTORY",
                        "商品itemId=" + item.getId() + "可用库存" + available + "，活动需要" + item.getSeckillStock(),
                        item.getId()));
            }
        }
        return issues;
    }

    public SeckillActivity offline(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "活动不存在");
        }
        if (!STATUS_ONLINE.equals(activity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不可下线");
        }
        List<SeckillItem> items = itemMapper.selectList(new LambdaQueryWrapper<SeckillItem>()
                .eq(SeckillItem::getActivityId, activityId));
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "活动还没有配置商品，无法下线");
        }
        for (SeckillItem item : items) {
            reserveService.release(item.getId());
        }
        activity.setStatus(STATUS_OFFLINE);
        activityMapper.updateById(activity);
        return activity;
    }
}
