package com.qixuan.commerce.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品卡片。
 *
 * 注意：SELECT 的列顺序必须与这里的组件顺序完全一致 ——
 * MyBatis 用 record 的规范构造器按位置映射，不做名字匹配。
 */
public record SeckillItemView(
        Long itemId,
        Long activityId,
        Long spuId,
        Long skuId,
        String title,
        String subtitle,
        String mainImageUrl,
        BigDecimal originalPrice,
        BigDecimal seckillPrice,
        Integer stock,
        Integer soldCount,
        LocalDateTime startAt,
        LocalDateTime endAt) {
}
