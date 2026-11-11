package com.qixuan.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qixuan.commerce.dto.ProductCardView;
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
                p.title AS name,
                c.name AS category_name,
                st.store_name AS store_name,
                MIN(sku.sale_price) AS min_price,
                COALESCE(SUM(inv.available_qty), 0) AS available_stock,
                CASE p.status WHEN 'ON_SALE' THEN 1 ELSE 0 END AS status
            FROM product_spu p
            JOIN store st ON p.store_id = st.id
            JOIN category c ON p.category_id = c.id
            LEFT JOIN product_sku sku
                ON sku.spu_id = p.id AND sku.status = 'ACTIVE'
            LEFT JOIN sku_inventory inv ON inv.sku_id = sku.id
            WHERE p.store_id = #{storeId}
            GROUP BY p.id, p.title, c.name, st.store_name, p.status
            ORDER BY p.id DESC
            """)
    List<ProductListItem> findListByStoreId(@Param("storeId") Long storeId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class, id = true),
            @Arg(column = "title", javaType = String.class),
            @Arg(column = "subtitle", javaType = String.class),
            @Arg(column = "main_image_url", javaType = String.class),
            @Arg(column = "min_price", javaType = BigDecimal.class),
            @Arg(column = "store_name", javaType = String.class),
            @Arg(column = "category_name", javaType = String.class),
            @Arg(column = "available_stock", javaType = Long.class),
            @Arg(column = "sold_count", javaType = Long.class)
    })
    @Select("""
            <script>
            SELECT
                p.id,
                p.title,
                p.subtitle,
                p.main_image_url,
                MIN(sku.sale_price) AS min_price,
                st.store_name,
                c.name AS category_name,
                COALESCE(SUM(inv.available_qty), 0) AS available_stock,
                COALESCE(SUM(inv.sold_qty), 0) AS sold_count
            FROM product_spu p
            JOIN store st ON st.id = p.store_id
            JOIN category c ON c.id = p.category_id
            LEFT JOIN product_sku sku
                ON sku.spu_id = p.id AND sku.status = 'ACTIVE'
            LEFT JOIN sku_inventory inv ON inv.sku_id = sku.id
            WHERE p.status = 'ON_SALE'
              AND st.status = 'ACTIVE'
            <if test="categoryId != null">
              AND p.category_id = #{categoryId}
            </if>
            <if test="keyword != null and keyword != ''">
              AND p.title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            GROUP BY p.id, p.title, p.subtitle, p.main_image_url, st.store_name, c.name
            ORDER BY p.id DESC
            LIMIT 60
            </script>
            """)
    List<ProductCardView> findOnSale(@Param("categoryId") Long categoryId,
            @Param("keyword") String keyword);
}
