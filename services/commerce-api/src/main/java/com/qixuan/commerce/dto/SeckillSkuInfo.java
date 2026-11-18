package com.qixuan.commerce.dto;

/**
 * 秒杀商品转正式订单时需要的 SKU / SPU 信息。
 *
 * SELECT 列顺序必须与组件顺序一致（record 按位置映射）。
 */
public record SeckillSkuInfo(
        Long skuId,
        Long spuId,
        String title,
        String imageUrl,
        String specJson,
        Long storeId,
        Integer reservedQty) {
}
