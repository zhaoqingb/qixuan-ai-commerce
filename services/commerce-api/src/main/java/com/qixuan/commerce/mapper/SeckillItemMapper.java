package com.qixuan.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qixuan.commerce.dto.SeckillItemView;
import com.qixuan.commerce.dto.SeckillSkuInfo;
import com.qixuan.commerce.entity.SeckillItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SeckillItemMapper extends BaseMapper<SeckillItem> {

    /**
     * 原子扣减秒杀库存（L0 基线方案）。
     *
     * "判断够不够" 和 "扣减" 在同一条 SQL 里，由 InnoDB 行锁保证不会两个事务同时通过检查。
     * 返回 0 表示库存不足 —— 这是唯一可信的判据，读出来再判断都不可靠。
     *
     * sold_count 同步递增，保证 seckill_stock + sold_count 恒等于初始库存，方便对账。
     */
    @Update("""
            UPDATE seckill_item
            SET seckill_stock = seckill_stock - #{qty},
                sold_count = sold_count + #{qty}
            WHERE id = #{itemId}
              AND seckill_stock >= #{qty}
            """)
    int deductStock(@Param("itemId") Long itemId, @Param("qty") Integer qty);

    /**
     * 回补秒杀库存（用户取消订单时用）。
     *
     * 和 deductStock 是一对反向操作：库存加回去、sold_count 减回来，
     * 保证 seckill_stock + sold_count 依然恒等于初始库存。
     *
     * WHERE 里带 sold_count >= qty 是防御：如果账已经漂了（sold_count 比要还的还少），
     * 宁可不更新也不能让 sold_count 变成负数 —— 负数会让守恒式失效，还会让后续对账算错。
     */
    @Update("""
            UPDATE seckill_item
            SET seckill_stock = seckill_stock + #{qty},
                sold_count = sold_count - #{qty}
            WHERE id = #{itemId}
              AND sold_count >= #{qty}
            """)
    int restoreStock(@Param("itemId") Long itemId, @Param("qty") Integer qty);

    /**
     * 秒杀商品列表（买家端展示用）。
     *
     * 三张表 JOIN：seckill_item 是活动内的商品，product_sku 提供原价，product_spu 提供标题和图片。
     * 只查 ONLINE 活动的商品 —— 活动没上线就不该出现在页面上。
     *
     * 注意 SELECT 列顺序必须与 SeckillItemView 的组件顺序一致，否则按位置映射会静默错位。
     */
    @Select("""
            SELECT
                si.id,
                si.activity_id,
                p.id AS spu_id,
                si.sku_id,
                p.title,
                p.subtitle,
                p.main_image_url,
                sku.sale_price,
                si.seckill_price,
                si.seckill_stock,
                si.sold_count,
                sa.start_at,
                sa.end_at
            FROM seckill_item si
            JOIN seckill_activity sa ON sa.id = si.activity_id
            JOIN product_sku sku ON sku.id = si.sku_id
            JOIN product_spu p ON p.id = sku.spu_id
            WHERE sa.status = 'ONLINE'
            ORDER BY sa.start_at DESC
            """)
    List<SeckillItemView> findOnlineItems();

    /**
     * 秒杀商品对应的 SKU / SPU 信息，转正式订单时用。
     *
     * 只在用户点「去支付」时调用一次，属于低频路径，直接查库不做缓存。
     */
    @Select("""
            SELECT
                si.sku_id,
                p.id AS spu_id,
                p.title,
                p.main_image_url,
                sku.spec_json,
                p.store_id,
                si.reserved_qty
            FROM seckill_item si
            JOIN product_sku sku ON sku.id = si.sku_id
            JOIN product_spu p ON p.id = sku.spu_id
            WHERE si.id = #{itemId}
            """)
    SeckillSkuInfo findSkuInfo(@Param("itemId") Long itemId);
}
