package com.qixuan.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qixuan.commerce.dto.CartItemView;
import com.qixuan.commerce.dto.CheckoutItemView;
import com.qixuan.commerce.entity.CartItem;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

        @ConstructorArgs({
                        @Arg(column = "cart_item_id", javaType = Long.class, id = true),
                        @Arg(column = "sku_id", javaType = Long.class),
                        @Arg(column = "quantity", javaType = Integer.class),
                        @Arg(column = "selected", javaType = Integer.class),
                        @Arg(column = "sku_code", javaType = String.class),
                        @Arg(column = "spec_json", javaType = String.class),
                        @Arg(column = "sale_price", javaType = BigDecimal.class),
                        @Arg(column = "product_title", javaType = String.class),
                        @Arg(column = "image_url", javaType = String.class),
                        @Arg(column = "available_qty", javaType = Long.class)
        })
        @Select("""
                        SELECT
                            ci.id AS cart_item_id,
                            ci.sku_id,
                            ci.quantity,
                            ci.selected,
                            sku.sku_code,
                            sku.spec_json,
                            sku.sale_price,
                            spu.title AS product_title,
                            spu.main_image_url AS image_url,
                            COALESCE(inv.available_qty, 0) AS available_qty
                        FROM cart_item ci
                        JOIN product_sku sku ON sku.id = ci.sku_id
                        JOIN product_spu spu ON spu.id = sku.spu_id
                        LEFT JOIN sku_inventory inv ON inv.sku_id = sku.id
                        WHERE ci.user_id = #{userId}
                        ORDER BY ci.id DESC
                        """)
        List<CartItemView> findByUserId(@Param("userId") Long userId);

        @ConstructorArgs({
                        @Arg(column = "cart_item_id", javaType = Long.class, id = true),
                        @Arg(column = "sku_id", javaType = Long.class),
                        @Arg(column = "spu_id", javaType = Long.class),
                        @Arg(column = "store_id", javaType = Long.class),
                        @Arg(column = "store_name", javaType = String.class),
                        @Arg(column = "product_title", javaType = String.class),
                        @Arg(column = "image_url", javaType = String.class),
                        @Arg(column = "spec_json", javaType = String.class),
                        @Arg(column = "sale_price", javaType = BigDecimal.class),
                        @Arg(column = "quantity", javaType = Integer.class),
                        @Arg(column = "available_qty", javaType = Long.class)
        })
        @Select("""
                        SELECT
                            ci.id AS cart_item_id,
                            ci.sku_id,
                            sku.spu_id,
                            spu.store_id,
                            st.store_name,
                            spu.title AS product_title,
                            spu.main_image_url AS image_url,
                            sku.spec_json,
                            sku.sale_price,
                            ci.quantity,
                            COALESCE(inv.available_qty, 0) AS available_qty
                        FROM cart_item ci
                        JOIN product_sku sku ON sku.id = ci.sku_id
                        JOIN product_spu spu ON spu.id = sku.spu_id
                        JOIN store st ON st.id = spu.store_id
                        LEFT JOIN sku_inventory inv ON inv.sku_id = sku.id
                        WHERE ci.user_id = #{userId} AND ci.selected = 1
                        ORDER BY spu.store_id, ci.id
                        """)
        List<CheckoutItemView> findSelectedForCheckout(@Param("userId") Long userId);
}
