package com.qixuan.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qixuan.commerce.entity.SkuInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SkuInventoryMapper extends BaseMapper<SkuInventory> {

    /**
     * 原子锁定库存：可售减 qty，锁定加 qty。
     * WHERE available_qty >= qty 由数据库保证不超卖，返回 0 表示库存不足。
     */
    @Update("""
            UPDATE sku_inventory
            SET available_qty = available_qty - #{qty},
                locked_qty = locked_qty + #{qty},
                version = version + 1
            WHERE sku_id = #{skuId}
              AND available_qty >= #{qty}
            """)
    int lockStock(@Param("skuId") Long skuId, @Param("qty") Integer qty);

    // 支付成功：锁定数量转已售。locked_qty不够说明数据已错乱，返回0
    @Update("""
            UPDATE sku_inventory
            SET locked_qty = locked_qty - #{qty},
                sold_qty = sold_qty + #{qty},
                version = version + 1
            WHERE sku_id = #{skuId}
              AND locked_qty >= #{qty}
            """)
    int confirmStock(@Param("skuId") Long skuId, @Param("qty") Integer qty);

    /**
     * 关单/取消：把锁定数量还给可售。返回 0 说明锁定数量不足，数据已错乱。
     */
    @Update("""
            UPDATE sku_inventory
            SET locked_qty = locked_qty - #{qty},
                available_qty = available_qty + #{qty},
                version = version + 1
            WHERE sku_id = #{skuId}
              AND locked_qty >= #{qty}
            """)
    int releaseStock(@Param("skuId") Long skuId, @Param("qty") Integer qty);

}
