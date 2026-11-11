package com.qixuan.commerce.mapper;

import com.qixuan.commerce.dto.OrderDetailRow;
import com.qixuan.commerce.dto.OrderItemRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qixuan.commerce.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("""
            SELECT COALESCE(SUM(oi.quantity), 0)
            FROM order_item oi
            JOIN store_order so ON so.id = oi.store_order_id
            WHERE so.trade_order_id = #{tradeOrderId}
            """)
    int countQuantityByTradeOrder(@Param("tradeOrderId") Long tradeOrderId);

    @ConstructorArgs({
            @Arg(column = "order_item_id", javaType = Long.class, id = true),
            @Arg(column = "trade_order_id", javaType = Long.class),
            @Arg(column = "order_no", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = String.class),
            @Arg(column = "goods_amount", javaType = BigDecimal.class),
            @Arg(column = "freight_amount", javaType = BigDecimal.class),
            @Arg(column = "payable_amount", javaType = BigDecimal.class),
            @Arg(column = "store_id", javaType = Long.class),
            @Arg(column = "store_name", javaType = String.class),
            @Arg(column = "spu_id", javaType = Long.class),
            @Arg(column = "sku_id", javaType = Long.class),
            @Arg(column = "product_title", javaType = String.class),
            @Arg(column = "image_url", javaType = String.class),
            @Arg(column = "spec_json", javaType = String.class),
            @Arg(column = "unit_price", javaType = BigDecimal.class),
            @Arg(column = "quantity", javaType = Integer.class),
            @Arg(column = "item_payable_amount", javaType = BigDecimal.class)
    })
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

    @ConstructorArgs({
            @Arg(column = "order_item_id", javaType = Long.class, id = true),
            @Arg(column = "trade_order_id", javaType = Long.class),
            @Arg(column = "order_no", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = String.class),
            @Arg(column = "goods_amount", javaType = BigDecimal.class),
            @Arg(column = "freight_amount", javaType = BigDecimal.class),
            @Arg(column = "discount_amount", javaType = BigDecimal.class),
            @Arg(column = "payable_amount", javaType = BigDecimal.class),
            @Arg(column = "address_snapshot", javaType = String.class),
            @Arg(column = "sub_order_no", javaType = String.class),
            @Arg(column = "sub_freight_amount", javaType = BigDecimal.class),
            @Arg(column = "sub_payable_amount", javaType = BigDecimal.class),
            @Arg(column = "store_id", javaType = Long.class),
            @Arg(column = "store_name", javaType = String.class),
            @Arg(column = "spu_id", javaType = Long.class),
            @Arg(column = "sku_id", javaType = Long.class),
            @Arg(column = "product_title", javaType = String.class),
            @Arg(column = "image_url", javaType = String.class),
            @Arg(column = "spec_json", javaType = String.class),
            @Arg(column = "unit_price", javaType = BigDecimal.class),
            @Arg(column = "quantity", javaType = Integer.class),
            @Arg(column = "item_payable_amount", javaType = BigDecimal.class)
    })
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
