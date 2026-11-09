package com.qixuan.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qixuan.commerce.dto.ProductListItem;
import com.qixuan.commerce.entity.ProductSpu;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductSpuMapper extends BaseMapper<ProductSpu> {

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),

            @Arg(column = "name", javaType = String.class),

            @Arg(column = "category_name", javaType = String.class),

            @Arg(column = "store_name", javaType = String.class),

            @Arg(column = "min_price", javaType = BigDecimal.class),

            @Arg(column = "available_stock", javaType = Long.class),

            @Arg(column = "status", javaType = Integer.class)
    })

    @Select("""
            SELECT
                p.id,
                p.name,
                c.name AS category_name,
                st.name AS store_name,
                MIN(sku.price) AS min_price,
                COALESCE(
                    SUM(inv.available_qty), 0
                ) AS available_stock,
                p.status
            FROM product_spu p

            JOIN store st
                ON p.store_id = st.id

            JOIN category c
                ON p.category_id = c.id

            LEFT JOIN product_sku sku
                ON sku.spu_id = p.id
                AND sku.status = 1

            LEFT JOIN sku_inventory inv
                ON inv.sku_id = sku.id

            WHERE p.store_id = #{storeId}

            GROUP BY
                p.id,
                p.name,
                c.name,
                st.name,
                p.status

            ORDER BY p.id DESC
            """)

    List<ProductListItem> findListByStoreId(
            @Param("storeId") Long storeId);
}
