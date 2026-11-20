package com.qixuan.commerce.service;

import com.qixuan.commerce.dto.CursorPage;
import com.qixuan.commerce.dto.OrderDetailRow;
import com.qixuan.commerce.dto.OrderItemRow;
import com.qixuan.commerce.dto.PageResult;
import com.qixuan.commerce.mapper.OrderItemMapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

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

    /** 单页默认条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 单页最大条数：和 MybatisPlusConfig 里的 maxLimit 保持一致，双重兜底 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 分页查订单明细。
     *
     * 参数在这里兜底，不能信前端：
     *   pageNo < 1        → 1（否则 LIMIT 的 offset 会算出负数）
     *   pageSize 越界     → 夹到 [1, 100]（否则一次拉全表，分页形同虚设）
     */
    public PageResult<OrderItemRow> page(Long userId, int pageNo, int pageSize) {

        int safePageNo = pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        if (safePageSize > MAX_PAGE_SIZE) {
            safePageSize = MAX_PAGE_SIZE;
        }

        Page<OrderItemRow> page = new Page<>(safePageNo, safePageSize);
        return PageResult.of(orderItemMapper.pageByUserId(page, userId));
    }

    /**
     * 游标分页：适合无限滚动、深分页场景。
     *
     * @param lastId 上一页最后一条的订单 id；第一次传 null 表示从头开始
     * @param size   本页条数
     *
     * 实现要点：多查一条来判断"还有没有下一页"。
     * 只查 size 条的话，无法区分"正好取完"和"后面还有"，会多一次空请求。
     */
    public CursorPage<OrderItemRow> cursor(Long userId, Long lastId, int size) {

        int safeSize = size < 1 ? DEFAULT_PAGE_SIZE : size;
        if (safeSize > MAX_PAGE_SIZE) {
            safeSize = MAX_PAGE_SIZE;
        }

        // 第一次没有游标，用 Long.MAX_VALUE 让 t.id < lastId 成立
        long cursor = lastId == null ? Long.MAX_VALUE : lastId;

        List<OrderItemRow> rows = orderItemMapper.findByCursor(userId, cursor, safeSize + 1);

        boolean hasMore = rows.size() > safeSize;
        if (hasMore) {
            rows = rows.subList(0, safeSize);
        }

        Long nextCursor = null;
        if (hasMore && !rows.isEmpty()) {
            nextCursor = rows.get(rows.size() - 1).tradeOrderId();
        }

        return new CursorPage<>(rows, nextCursor, hasMore);
    }

    public List<OrderDetailRow> detail(String orderNo, Long userId) {
        return orderItemMapper.findDetailByOrderNo(orderNo, userId);
    }
}
