package com.qixuan.commerce.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

import com.qixuan.commerce.entity.AiActionApproval;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AiApprovalService.describe 的边界测试。
 *
 * describe 把审批单的 payload JSON 翻译成中文描述给用户看。
 * 它出错的后果：用户看到的审批内容是错的，可能误点通过。
 */
class AiApprovalServiceTest {

    private final AiApprovalService service = new AiApprovalService(
            null, null, null, null, null, JsonMapper.builder().build());

    private String describe(AiActionApproval approval) {
        return ReflectionTestUtils.invokeMethod(service, "describe", approval);
    }

    private AiActionApproval approval(String action, String payload) {
        AiActionApproval a = new AiActionApproval();
        a.setActionCode(action);
        a.setRequestPayload(payload);
        return a;
    }

    @Test
    void describe_补货() {
        AiActionApproval a = approval("RESTOCK",
                "{\"skuId\":7,\"qty\":25}");
        assertEquals("给 SKU 7 补货 25 件", describe(a));
    }

    @Test
    void describe_发货() {
        AiActionApproval a = approval("SHIP",
                "{\"subOrderNo\":\"Q123-1\",\"carrierCode\":\"SF\",\"trackingNo\":\"SF999\"}");
        assertEquals("给子单 Q123-1 发货（SF SF999）", describe(a));
    }

    @Test
    void describe_未知动作() {
        AiActionApproval a = approval("UNKNOWN_ACTION", "{}");
        assertEquals("UNKNOWN_ACTION", describe(a), "未知动作原样返回 actionCode");
    }

    @Test
    void describe_非法JSON降级() {
        AiActionApproval a = approval("RESTOCK", "坏掉的json");
        assertEquals("RESTOCK", describe(a), "解析失败要降级到 actionCode，不能抛异常");
    }

    @Test
    void describe_缺字段降级() {
        AiActionApproval a = approval("RESTOCK", "{}");
        assertEquals("给 SKU 0 补货 0 件", describe(a), "缺字段时 Jackson 返回 0，不抛异常");
    }
}
