package com.qixuan.commerce.common;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AgentTools 的边界测试。
 *
 * parseLimit 是私有方法，用反射调用 —— 它不依赖任何外部状态，
 * 是纯粹的输入输出，正适合"给边界值、断言结果"这种最简单的测试写法。
 */
class AgentToolsTest {

    private final AgentTools tools = new AgentTools(
            null, null, null, null, null, null, null, null, null,
            JsonMapper.builder().build(), null, null);

    private int parseLimit(String json) {
        return ReflectionTestUtils.invokeMethod(tools, "parseLimit", json);
    }

    @Test
    void 缺省值边界() {
        assertEquals(5, parseLimit(null), "null 应回退到默认 5");
        assertEquals(5, parseLimit(""), "空串应回退到默认 5");
        assertEquals(5, parseLimit("{}"), "没带 limit 字段应回退到默认 5");
        assertEquals(5, parseLimit("坏掉的json"), "解析失败应回退到默认 5");
    }

    @Test
    void 非法值边界() {
        assertEquals(5, parseLimit("{\"limit\":0}"), "0 非法，回退默认");
        assertEquals(5, parseLimit("{\"limit\":-3}"), "负数非法，回退默认");
    }

    @Test
    void 合法值与上限() {
        assertEquals(3, parseLimit("{\"limit\":3}"), "正常值原样返回");
        assertEquals(10, parseLimit("{\"limit\":100}"), "超过上限应截断到 10");
    }
}
