package com.qixuan.commerce.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AiChatService.quote 的边界测试。
 *
 * 这个私有方法负责把字符串转成合法 JSON 字面量，纯输入输出，不碰数据库和网络。
 * 它出错的后果是 SSE 推送非法 JSON、前端解析失败 —— 值得用几个边界字符守住。
 */
class AiChatServiceQuoteTest {

    private final AiChatService service = new AiChatService(
            null, null, null, null, null, null);

    private String quote(String value) {
        return ReflectionTestUtils.invokeMethod(service, "quote", value);
    }

    @Test
    void 普通字符串() {
        assertEquals("\"hello\"", quote("hello"));
        assertEquals("\"\"", quote(""));
    }

    @Test
    void JSON特殊字符() {
        assertEquals("\"a\\\"b\"", quote("a\"b"), "双引号要转义");
        assertEquals("\"a\\\\b\"", quote("a\\b"), "反斜杠要转义");
        assertEquals("\"a\\nb\"", quote("a\nb"), "换行要转义");
        assertEquals("\"a\\tb\"", quote("a\tb"), "制表符要转义");
        assertEquals("\"a\\rb\"", quote("a\rb"), "回车要转义");
    }

    @Test
    void 控制字符() {
        assertEquals("\"a\\u0000b\"", quote("a\u0000b"), "控制字符要用 \\u 表示");
        assertEquals("\"a\\u001fb\"", quote("a\u001fb"), "边界控制字符");
    }
}
