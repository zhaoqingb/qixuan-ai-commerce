package com.qixuan.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qixuan.commerce.entity.SeckillOrder;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    @Insert("""
                <script>
                    INSERT IGNORE INTO seckill_order
                        (order_no, activity_id, item_id, user_id, sku_id, quantity, amount, status, active)
                    VALUES
                    <foreach collection="list" item="o" separator=",">
                        (#{o.orderNo}, #{o.activityId}, #{o.itemId}, #{o.userId}, #{o.skuId}, 1, #{o.amount}, #{o.status}, 1)
                    </foreach>
                </script>
            """)
    int insertBatch(@Param("list") List<SeckillOrder> list);
}
