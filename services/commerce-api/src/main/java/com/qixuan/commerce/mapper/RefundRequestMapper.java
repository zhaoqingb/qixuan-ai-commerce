package com.qixuan.commerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qixuan.commerce.entity.RefundRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefundRequestMapper extends BaseMapper<RefundRequest> {

    /**
     * 某个订单明细已经占用的退款数量。
     * 只统计 REQUESTED / APPROVED —— 被拒绝的不占额度，否则用户被拒一次就再也退不了了。
     */
    @Select("""
            SELECT COALESCE(SUM(quantity), 0)
            FROM refund_request
            WHERE order_item_id = #{orderItemId}
              AND status IN ('REQUESTED', 'APPROVED')
            """)
    int sumRefundedQuantity(@Param("orderItemId") Long orderItemId);
}
