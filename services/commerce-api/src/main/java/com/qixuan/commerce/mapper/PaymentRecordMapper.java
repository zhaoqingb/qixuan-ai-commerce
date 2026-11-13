package com.qixuan.commerce.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

// 支付流水：退款、对账
@Mapper
public interface PaymentRecordMapper {
    @Insert("""
            INSERT INTO payment_record
                (pay_no, trade_order_id, provider, provider_trade_no, amount, status, paid_at)
            VALUES
                (#{payNo}, #{tradeOrderId}, #{provider}, #{providerTradeNo}, #{amount}, #{status}, #{paidAt})
            """)
    int insert(@Param("payNo") String payNo,
            @Param("tradeOrderId") Long tradeOrderId,
            @Param("provider") String provider,
            @Param("providerTradeNo") String providerTradeNo,
            @Param("amount") BigDecimal amount,
            @Param("status") String status,
            @Param("paidAt") LocalDateTime paidAt);
}
