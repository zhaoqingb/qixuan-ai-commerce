package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.qixuan.commerce.dto.SeckillItemView;
import com.qixuan.commerce.dto.SeckillResult;
import com.qixuan.commerce.entity.SeckillActivity;
import com.qixuan.commerce.entity.SeckillItem;
import com.qixuan.commerce.entity.SeckillOrder;
import com.qixuan.commerce.mapper.SeckillActivityMapper;
import com.qixuan.commerce.mapper.SeckillItemMapper;
import com.qixuan.commerce.mapper.SeckillOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 秒杀（L1：Redis 预扣版）。
 *
 * 请求的处理顺序：
 * 1. 查商品、校验活动时间 —— 这两步走缓存化的主键查询，几乎不花钱
 * 2. Redis 原子预扣 —— 绝大多数请求死在这里，不碰数据库
 * 3. 只有拿到名额的那 N 个请求（N = 库存数）才下探到 MySQL 落库
 *
 * 为什么 L0 只有 240 QPS：每个请求都要 UPDATE 同一行，
 * 行锁让 1000 个请求串行通过，而且前几步无论成败都要白做一遍。
 */
@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);

    /** 活动状态：已上线 */
    private static final String ACTIVITY_ONLINE = "ONLINE";

    /** 秒杀订单状态：已抢到（待转正式订单） */
    private static final String ORDER_SUCCESS = "SUCCESS";

    /** 秒杀订单状态：已取消（名额已释放） */
    private static final String ORDER_CANCELLED = "CANCELLED";

    private final SeckillActivityMapper activityMapper;
    private final SeckillItemMapper itemMapper;
    private final SeckillOrderMapper orderMapper;
    private final SeckillStockService stockService;
    private final SeckillOrderProducer orderProducer;
    private final SeckillItemCache itemCache;
    private final TransactionTemplate transactionTemplate;

    public SeckillService(SeckillActivityMapper activityMapper, SeckillItemMapper itemMapper,
            SeckillOrderMapper orderMapper, SeckillStockService stockService,
            SeckillOrderProducer orderProducer, SeckillItemCache itemCache,
            TransactionTemplate transactionTemplate) {
        this.activityMapper = activityMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.stockService = stockService;
        this.orderProducer = orderProducer;
        this.itemCache = itemCache;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 秒杀订单号。
     *
     * 原来用「时间戳 + 4 位随机数」，每秒几百单时会大量碰撞（实测 500 单撞 9 次）——
     * 这是生日悖论：n 个样本落在 9000 个格子里，期望碰撞约 n²/(2*9000)。
     *
     * 换成雪花 ID：workerId + 毫秒时间戳 + 序列号，同毫秒内靠序列号区分，保证唯一。
     */
    private String generateOrderNo() {
        return "SK" + IdWorker.getIdStr();
    }

    /**
     * 秒杀商品列表（买家端展示）。
     *
     * 库存用 Redis 的实时值：DB 的 seckill_stock 要等异步落库才更新，
     * 高并发期间会短暂滞后，直接展示会让用户以为"还有货"点进去却抢不到。
     */
    public List<SeckillItemView> listOnline() {
        List<SeckillItemView> rows = itemMapper.findOnlineItems();
        List<SeckillItemView> result = new ArrayList<>(rows.size());

        for (SeckillItemView row : rows) {
            Integer live = stockService.currentStock(row.itemId());
            int stock = live == null ? row.stock() : live;
            result.add(new SeckillItemView(
                    row.itemId(), row.activityId(), row.spuId(), row.skuId(),
                    row.title(), row.subtitle(), row.mainImageUrl(),
                    row.originalPrice(), row.seckillPrice(),
                    stock, row.soldCount(), row.startAt(), row.endAt()));
        }
        return result;
    }

    /**
     * 当前用户的秒杀记录（含已取消的）。
     *
     * 秒杀订单目前还没接进 trade_order，所以「我的订单」页面看不到它们，
     * 这个接口就是用户唯一能回看自己抢过什么的入口。
     * 等转正式订单的链路做通之后，这里可以退化成"仅用于展示秒杀历史"。
     */
    public List<SeckillOrder> listMyOrders(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getUserId, userId)
                .orderByDesc(SeckillOrder::getId)
                .last("LIMIT 50"));
    }

    /**
     * 取消秒杀订单，把名额还回去。
     *
     * 顺序是这套逻辑的全部要点：**先 DB，后 Redis**。
     *
     * 反过来（先加 Redis 再加 DB）一旦 DB 失败，Redis 就凭空多出一个名额 —— 超卖。
     * 而先 DB 成功、Redis 失败只是少卖一个，用户重试还能抢到，
     * 并且对账不会帮倒忙（对账只往下修）。
     *
     * 幂等：已经取消过的直接返回成功，不重复回补。
     */
    public SeckillResult cancel(Long userId, String orderNo) {

        SeckillOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo)
                .eq(SeckillOrder::getUserId, userId));
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "秒杀订单不存在");
        }
        if (ORDER_CANCELLED.equals(order.getStatus())) {
            return new SeckillResult(orderNo, ORDER_CANCELLED, "订单已取消");
        }
        if (!ORDER_SUCCESS.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不可取消");
        }

        Boolean done = transactionTemplate.execute(status -> {
            // active 必须显式置 NULL：唯一索引是 (user_id, item_id, active)，
            // 只有把 active 变 NULL 才能让出这个位置，用户才能再抢一次。
            // 注意用 wrapper.set 而不是 patch.setActive(null) —— 后者的 null 会被
            //「更新时忽略 null 字段」的默认策略跳过，那一列根本不会被写。
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<SeckillOrder>()
                    .set(SeckillOrder::getStatus, ORDER_CANCELLED)
                    .set(SeckillOrder::getActive, null)
                    .eq(SeckillOrder::getId, order.getId())
                    .eq(SeckillOrder::getStatus, ORDER_SUCCESS));
            if (rows != 1) {
                // 状态被别人改过（并发取消 / 后台已转正式订单）
                return false;
            }

            if (itemMapper.restoreStock(order.getItemId(), 1) != 1) {
                // 账已经漂了：宁可这条取消失败，也不能把 sold_count 减成负数
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "库存回补异常，请联系客服");
            }
            return true;
        });

        if (!Boolean.TRUE.equals(done)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单状态已变化，请刷新后重试");
        }

        // 事务已提交，现在才动 Redis
        stockService.release(order.getItemId(), userId);

        log.info("秒杀订单已取消，名额已释放 orderNo={} itemId={} userId={}",
                orderNo, order.getItemId(), userId);
        return new SeckillResult(orderNo, ORDER_CANCELLED, "已取消，名额已释放");
    }

    /**
     * 抢购。
     *
     * 顺序很重要：先扣库存，再落订单。
     * 反过来（先插订单再扣库存）会让所有请求都先做一次 INSERT，
     * 而 INSERT 比一次条件 UPDATE 贵得多 —— 秒杀场景要让"抢不到"尽早失败。
     */

    public SeckillResult buy(Long userId, Long itemId) {

        // 本地缓存命中就不查库。抢不到的人也要走这里，所以这两次查询的省与不省很关键。
        SeckillItemCache.Cached cached = itemCache.get(itemId);
        if (cached == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "秒杀商品不存在");
        }
        SeckillItem item = cached.item();
        SeckillActivity activity = cached.activity();

        LocalDateTime now = LocalDateTime.now();
        if (activity == null || !ACTIVITY_ONLINE.equals(activity.getStatus()) || now.isBefore(activity.getStartAt())
                || now.isAfter(activity.getEndAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "秒杀活动未开始或已结束");
        }

        int result = stockService.tryDeduct(itemId, userId);

        // 第一优先：能自己恢复的失败。库存没预热就灌一次再试，
        // 这样压测前忘了 sync 也不会全军覆没。
        //
        // 注意这里必须重新查库，不能用 item.getSeckillStock() —— 缓存最长 5 秒陈旧，
        // 这 5 秒里如果已经卖掉一部分，用旧值灌 Redis 等于凭空多放名额，直接超卖。
        if (result == SeckillStockService.NOT_READY) {
            try {
                SeckillItem latest = itemMapper.selectById(itemId);
                if (latest != null) {
                    stockService.loadStock(itemId, latest.getSeckillStock());
                    result = stockService.tryDeduct(itemId, userId);
                }
            } catch (Exception ex) {
                log.warn("秒杀库存预热失败，itemId={}", itemId, ex);
            }
        }

        // 第二优先：明确的业务失败，各给各的提示。
        // 「已购买」和「已售罄」对用户是不同的信息：前者不用再试，后者下次还得来。
        if (result == SeckillStockService.DUPLICATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "每人限购1件");
        }
        if (result == SeckillStockService.SOLD_OUT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "手慢了，已售罄");
        }

        // 最后兜底：走到这里只可能是 OK。不是的话说明 Redis 出了状况，不能放行。
        if (result != SeckillStockService.OK) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "秒杀暂时不可用，请稍后再试");
        }

        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setItemId(itemId);
        order.setActivityId(activity.getId());
        order.setSkuId(item.getSkuId());
        order.setAmount(item.getSeckillPrice());
        order.setStatus(ORDER_SUCCESS);
        // 投递到 Kafka。同步发送，失败当场知道 —— 语义和之前"入队失败"完全一致
        if (!orderProducer.send(order)) {
            stockService.rollback(itemId, userId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "秒杀暂时不可用，请稍后再试");
        }
        return new SeckillResult(order.getOrderNo(), "QUEUING", "抢购成功，订单创建中");
    }
}
