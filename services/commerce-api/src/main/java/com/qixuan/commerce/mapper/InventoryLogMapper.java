package com.qixuan.commerce.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryLogMapper {
    @Insert("""
            INSERT INTO inventory_log (sku_id, business_no, operation, quantity)
            VALUES (#{skuId}, #{businessNo}, #{operation}, #{quantity})
            """)
    int insert(@Param("skuId") Long skuId,
            @Param("businessNo") String businessNo,
            @Param("operation") String operation,
            @Param("quantity") Integer quantity);
}