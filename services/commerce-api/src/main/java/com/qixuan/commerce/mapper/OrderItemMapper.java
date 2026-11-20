package com.qixuan.commerce.mapper;

import com.qixuan.commerce.dto.OrderDetailRow;
import com.qixuan.commerce.dto.OrderItemRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qixuan.commerce.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

        @Select("""
                        SELECT COALESCE(SUM(oi.quantity), 0)
                        FROM order_item oi
                        JOIN store_order so ON so.id = oi.store_order_id
                        WHERE so.trade_order_id = #{tradeOrderId}
                        """)
        int countQuantityByTradeOrder(@Param("tradeOrderId") Long tradeOrderId);

        /**
         * 结果映射说明：
         * SELECT 的列顺序必须与 OrderItemRow 的 record 组件顺序完全一致，
         * MyBatis 按位置调用 record 的规范构造器完成映射，无需 @ConstructorArgs。
         */
        @Select("""
                        SELECT
                            oi.id AS order_item_id,
                            t.id AS trade_order_id,
                            t.order_no,
                            t.status,
                            DATE_FORMAT(t.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                            t.goods_amount,
                            t.freight_amount,
                            t.payable_amount,
                            so.store_id,
                            st.store_name,
                            oi.spu_id,
                            oi.sku_id,
                            oi.product_title,
                            oi.image_url_snapshot AS image_url,
                            oi.sku_snapshot AS spec_json,
                            oi.unit_price,
                            oi.quantity,
                            oi.payable_amount AS item_payable_amount
                        FROM trade_order t
                        JOIN store_order so ON so.trade_order_id = t.id
                        JOIN store st ON st.id = so.store_id
                        JOIN order_item oi ON oi.store_order_id = so.id
                        WHERE t.user_id = #{userId}
                        ORDER BY t.id DESC, so.id ASC, oi.id ASC
                        """)
        List<OrderItemRow> findByUserId(@Param("userId") Long userId);

        /**
         * 分页查订单明细。
         *
         * 与 findByUserId 的区别只在返回值：Page 参数必须放第一位，MyBatis-Plus 靠位置识别，
         * 它会把 SQL 改写成带 LIMIT 的形式，并额外执行一条 COUNT 填充 total。
         *
         * ORDER BY 不能省：LIMIT 本身不保证顺序，无排序时翻页会出现重复或漏数据。
         */
        @Select("""
                        SELECT
                            oi.id AS order_item_id,
                            t.id AS trade_order_id,
                            t.order_no,
                            t.status,
                            DATE_FORMAT(t.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                            t.goods_amount,
                            t.freight_amount,
                            t.payable_amount,
                            so.store_id,
                            st.store_name,
                            oi.spu_id,
                            oi.sku_id,
                            oi.product_title,
                            oi.image_url_snapshot AS image_url,
                            oi.sku_snapshot AS spec_json,
                            oi.unit_price,
                            oi.quantity,
                            oi.payable_amount AS item_payable_amount
                        FROM trade_order t
                        JOIN store_order so ON so.trade_order_id = t.id
                        JOIN store st ON st.id = so.store_id
                        JOIN order_item oi ON oi.store_order_id = so.id
                        WHERE t.user_id = #{userId}
                        ORDER BY t.id DESC, so.id ASC, oi.id ASC
                        """)
        Page<OrderItemRow> pageByUserId(Page<OrderItemRow> page, @Param("userId") Long userId);

        /**
         * 游标分页：从 lastId 往前取 limit 条。
         *
         * 和 pageByUserId 的关键差别：这里没有 OFFSET。
         * OFFSET 1000000 会让 MySQL 先读出 100 万行再丢掉，翻得越深越慢；
         * 而 `t.id < lastId` 直接走主键索引定位，翻到第几页耗时都一样。
         *
         * 代价：只能顺序往下翻，不能跳页。
         */
        @Select("""
                        SELECT
                            oi.id AS order_item_id,
                            t.id AS trade_order_id,
                            t.order_no,
                            t.status,
                            DATE_FORMAT(t.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                            t.goods_amount,
                            t.freight_amount,
                            t.payable_amount,
                            so.store_id,
                            st.store_name,
                            oi.spu_id,
                            oi.sku_id,
                            oi.product_title,
                            oi.image_url_snapshot AS image_url,
                            oi.sku_snapshot AS spec_json,
                            oi.unit_price,
                            oi.quantity,
                            oi.payable_amount AS item_payable_amount
                        FROM trade_order t
                        JOIN store_order so ON so.trade_order_id = t.id
                        JOIN store st ON st.id = so.store_id
                        JOIN order_item oi ON oi.store_order_id = so.id
                        WHERE t.user_id = #{userId}
                          AND t.id < #{lastId}
                        ORDER BY t.id DESC, so.id ASC, oi.id ASC
                        LIMIT #{limit}
                        """)
        List<OrderItemRow> findByCursor(@Param("userId") Long userId,
                        @Param("lastId") Long lastId,
                        @Param("limit") int limit);

        /**
         * 结果映射说明：列顺序需与 OrderDetailRow 的 record 组件顺序一致。
         */
        @Select("""
                        SELECT
                            oi.id AS order_item_id,
                            t.id AS trade_order_id,
                            t.order_no,
                            t.status,
                            DATE_FORMAT(t.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                            t.goods_amount,
                            t.freight_amount,
                            t.discount_amount,
                            t.payable_amount,
                            so.address_snapshot,
                            so.sub_order_no,
                            so.freight_amount AS sub_freight_amount,
                            so.payable_amount AS sub_payable_amount,
                            so.store_id,
                            st.store_name,
                            oi.spu_id,
                            oi.sku_id,
                            oi.product_title,
                            oi.image_url_snapshot AS image_url,
                            oi.sku_snapshot AS spec_json,
                            oi.unit_price,
                            oi.quantity,
                            oi.payable_amount AS item_payable_amount
                        FROM trade_order t
                        JOIN store_order so ON so.trade_order_id = t.id
                        JOIN store st ON st.id = so.store_id
                        JOIN order_item oi ON oi.store_order_id = so.id
                        WHERE t.user_id = #{userId} AND t.order_no = #{orderNo}
                        ORDER BY so.id ASC, oi.id ASC
                        """)
        List<OrderDetailRow> findDetailByOrderNo(@Param("orderNo") String orderNo,
                        @Param("userId") Long userId);

}
