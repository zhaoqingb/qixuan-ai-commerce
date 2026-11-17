package com.qixuan.commerce.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型客户端。走 OpenAI 兼容协议（DashScope 的 /compatible-mode/v1）。
 *
 * 刻意不使用 RestClient 的对象序列化转换器，而是自己用 JsonMapper 序列化/反序列化：
 * 项目是 Boot 4 + Jackson 3（tools.jackson），转换成 Map/JsonNode 时版本坑比较多，
 * 收发原始 String 最稳。
 */
@Component
public class LlmClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String model;

    public LlmClient(
            @Value("${ai.base-url}") String baseUrl,
            @Value("${ai.api-key}") String apiKey,
            @Value("${ai.model}") String model,
            JsonMapper jsonMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 发一次对话，返回助手的纯文本回复。
     *
     * @param messages OpenAI 格式的消息列表，每条形如 {"role":"user","content":"..."}
     */
    public String chat(List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        JsonNode root = post("/chat/completions", body);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() || content.isNull() ? "" : content.asText();
    }

    /**
     * 带工具的一次调用。返回模型的原话、它想调的工具、以及**原始的 assistant 消息**。
     *
     * 返回 rawMessage 是为了让调用方把它原样塞回上下文 —— 少了它，后面的 tool 消息
     * 会因为"没有对应的 tool_calls"而报错。
     */
    public LlmResult chatWithTools(List<Map<String, Object>> messages,
            List<Map<String, Object>> tools) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        JsonNode root = post("/chat/completions", body);
        JsonNode messageNode = root.path("choices").path(0).path("message");

        JsonNode contentNode = messageNode.path("content");
        String content = (contentNode.isMissingNode() || contentNode.isNull())
                ? null
                : contentNode.asText();

        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode tc : messageNode.path("tool_calls")) {
            calls.add(new ToolCall(
                    tc.path("id").asText(),
                    tc.path("function").path("name").asText(),
                    tc.path("function").path("arguments").asText()));
        }

        // 手动重建 assistant 消息，字段和模型返回的结构一致
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("role", "assistant");
        raw.put("content", content);
        if (!calls.isEmpty()) {
            List<Map<String, Object>> rawCalls = new ArrayList<>();
            for (ToolCall call : calls) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", call.name());
                fn.put("arguments", call.arguments());

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", call.id());
                item.put("type", "function");
                item.put("function", fn);
                rawCalls.add(item);
            }
            raw.put("tool_calls", rawCalls);
        }

        return new LlmResult(content, calls, raw);
    }

    /** 统一的 HTTP 调用：发 JSON 字符串，收 JSON 字符串，解析成树 */
    protected JsonNode post(String path, Map<String, Object> body) {
        String raw = restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("大模型返回为空");
        }
        return jsonMapper.readTree(raw);
    }

    protected String getModel() {
        return model;
    }

    /** 模型要求调用的一个工具 */
    public record ToolCall(String id, String name, String arguments) {
    }

    /** 一次模型调用结果：原话 + 想调的工具 + 原始 assistant 消息（要原样回填上下文） */
    public record LlmResult(String content, List<ToolCall> toolCalls,
            Map<String, Object> rawMessage) {

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
