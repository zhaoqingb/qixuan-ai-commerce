package com.qixuan.commerce.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

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

        return new LlmResult(content, calls, buildRawMessage(content, calls));
    }

    /**
     * 流式调用：文字增量通过 onContent 实时回调，工具调用在内部按 index 累加。
     *
     * 四个容易翻车的细节：
     *   1. arguments 是字符串片段，流完之前几乎一定不是合法 JSON —— 中途绝对不能 parse
     *   2. 一次回复可能有多个 tool_call，delta 交错到达，必须按 index 分桶
     *   3. index 不保证从 0 连续递增，也不保证顺序到达 —— 用 TreeMap 而不是数组
     *   4. content 和 tool_calls 在同一条流里交替出现 —— 各走各的通道，别混在一起
     *
     * @param onContent 每收到一段文字就回调（用于往 SSE 推给前端）
     */
    public LlmResult chatWithToolsStream(List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            Consumer<String> onContent) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        StringBuilder content = new StringBuilder();
        // index -> [id, name]；arguments 单独用 StringBuilder 追加（纯字符串拼接）
        Map<Integer, String[]> meta = new TreeMap<>();
        Map<Integer, StringBuilder> argsBuf = new HashMap<>();

        restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonMapper.writeValueAsString(body))
                .exchange((request, response) -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // SSE 每行形如 "data: {...}"，空行是事件分隔符，忽略即可
                            if (!line.startsWith("data:")) {
                                continue;
                            }
                            String payload = line.substring(5).trim();
                            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                                continue;
                            }

                            JsonNode chunk = jsonMapper.readTree(payload);
                            JsonNode delta = chunk.path("choices").path(0).path("delta");

                            JsonNode contentNode = delta.path("content");
                            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                                String text = contentNode.asText();
                                if (!text.isEmpty()) {
                                    content.append(text);
                                    if (onContent != null) {
                                        onContent.accept(text);
                                    }
                                }
                            }

                            for (JsonNode tc : delta.path("tool_calls")) {
                                int index = tc.path("index").asInt(0);
                                meta.computeIfAbsent(index, k -> new String[]{"", ""});
                                if (tc.hasNonNull("id")) {
                                    meta.get(index)[0] = tc.path("id").asText();
                                }
                                JsonNode fn = tc.path("function");
                                if (fn.hasNonNull("name")) {
                                    meta.get(index)[1] = fn.path("name").asText();
                                }
                                if (fn.hasNonNull("arguments")) {
                                    argsBuf.computeIfAbsent(index, k -> new StringBuilder())
                                            .append(fn.path("arguments").asText());
                                }
                            }
                        }
                    }
                    return null;
                });

        // 流结束后才拼接并解析参数：中途解析半截 JSON 只会得到异常
        List<ToolCall> calls = new ArrayList<>();
        for (Map.Entry<Integer, String[]> entry : meta.entrySet()) {
            String[] slot = entry.getValue();
            StringBuilder buf = argsBuf.get(entry.getKey());
            String args = buf == null ? "" : buf.toString();
            calls.add(new ToolCall(slot[0], slot[1], args.isEmpty() ? "{}" : args));
        }

        String text = content.length() == 0 ? null : content.toString();
        return new LlmResult(text, calls, buildRawMessage(text, calls));
    }

    /** 把模型返回的 assistant 消息还原成 OpenAI 格式，回填上下文时用 */
    private Map<String, Object> buildRawMessage(String content, List<ToolCall> calls) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("role", "assistant");
        raw.put("content", content);
        if (calls != null && !calls.isEmpty()) {
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
        return raw;
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
