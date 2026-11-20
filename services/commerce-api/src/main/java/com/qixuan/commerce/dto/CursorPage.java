package com.qixuan.commerce.dto;

import java.util.List;

/**
 * 游标分页结果。
 *
 * 和 PageResult 的区别：
 *   PageResult  带页码，能跳页，但 offset 越大扫描行数越多（深分页退化）
 *   CursorPage  只带"下一页从哪开始"，每次都从索引定位，深度与翻页次数无关
 *
 * 代价是不能跳页，只能顺序往下翻 —— 移动端列表、无限滚动正是这个模式。
 *
 * nextCursor 是下一页的游标（当前页最后一条的订单 id）；hasMore 为 false 时为 null。
 */
public record CursorPage<T>(List<T> records, Long nextCursor, boolean hasMore) {
}
