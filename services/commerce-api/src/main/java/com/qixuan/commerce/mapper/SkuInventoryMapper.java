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
}
