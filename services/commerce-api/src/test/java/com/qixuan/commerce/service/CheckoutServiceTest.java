package com.qixuan.commerce.service;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.qixuan.commerce.dto.CheckoutPreviewResponse.StoreGroup;
import com.qixuan.commerce.dto.CheckoutPreviewResponse.StoreItem;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CheckoutService 的纯逻辑边界测试。
 *
 * sumQuantity 出错 → 结算数量算错，直接影响金额；
 * jsonString 出错 → 订单快照里的 JSON 非法。
 */
class CheckoutServiceTest {

    private final CheckoutService service = new CheckoutService(
            null, null, null, null, null, null, null);

    private int sumQuantity(StoreGroup group) {
        return ReflectionTestUtils.invokeMethod(service, "sumQuantity", group);
    }

    private String jsonString(String value) {
        return ReflectionTestUtils.invokeMethod(CheckoutService.class, "jsonString", value);
    }

    private StoreItem item(Integer quantity) {
        return new StoreItem(1L, 2L, 3L, "商品", "url", "{}",
                BigDecimal.TEN, quantity, 100L, true, BigDecimal.TEN);
    }

    private StoreGroup group(List<StoreItem> items) {
        return new StoreGroup(1L, "店铺", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, items);
    }

    @Test
    void sumQuantity_空列表() {
        assertEquals(0, sumQuantity(group(List.of())), "空列表数量应为 0");
    }

    @Test
    void sumQuantity_多项累加() {
        assertEquals(5, sumQuantity(group(List.of(item(2), item(3)))),
                "2+3 应等于 5");
    }

    @Test
    void jsonString_null与空串() {
        assertEquals("null", jsonString(null), "null 要输出 JSON 的 null 字面量");
        assertEquals("\"\"", jsonString(""), "空串要输出空 JSON 字符串");
    }

    @Test
    void jsonString_特殊字符() {
        assertEquals("\"a\\\"b\"", jsonString("a\"b"));
        assertEquals("\"a\\\\b\"", jsonString("a\\b"));
        assertEquals("\"a\\nb\"", jsonString("a\nb"));
        assertEquals("\"a\\rb\"", jsonString("a\rb"));
        assertEquals("\"a\\tb\"", jsonString("a\tb"));
    }

    @Test
    void jsonString_控制字符() {
        assertEquals("\"\\u0000\"", jsonString("\u0000"));
        assertEquals("\"\\u001f\"", jsonString("\u001f"));
    }
}
