package com.qixuan.commerce.service;

import com.qixuan.commerce.common.AgentTools;
import com.qixuan.commerce.common.LlmClient;
import com.qixuan.commerce.dto.ChatRequest;
import com.qixuan.commerce.dto.ChatResponse;
import com.qixuan.commerce.entity.AiConversation;
import com.qixuan.commerce.entity.AiMessage;
import com.qixuan.commerce.mapper.AiConversationMapper;
import com.qixuan.commerce.mapper.AiMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String SCENE_BUYER = "BUYER";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_TOOL = "tool";

    /** 连续几次工具失败就熔断，避免模型在同一个坑里反复试 */
    private static final int MAX_CONSECUTIVE_FAILURES = 2;

    /** 单次对话的时间预算：模型再聪明也不能让用户干等 */
    private static final long TASK_TIME_BUDGET_MS = 30_000L;

    /** 只带最近 N 条历史，防止上下文无限增长把 token 撑爆 */
    private static final int MAX_HISTORY = 20;

    /** 取消标志的 Redis key 前缀，按会话隔离 */
    private static final String CANCEL_KEY_PREFIX = "ai:cancel:";

    /** Agent 最多转几圈。没有上限的循环 = 无限烧钱 */
    private static final int MAX_ROUNDS = 5;

    private static final String SYSTEM_PROMPT = """
            你是「栖选商城」的智能助手，服务买家和商家。

            买家：查订单、查商品价格与库存、售后咨询。
            商家：查店铺商品与库存、查待发货订单、补货、发货。

            工作方式：
            - 需要真实数据时调用工具，不要凭印象回答。
            - 工具参数缺 id 时，先用查询类工具拿到；用户不掌握内部 ID，不要向他们索取。
            - 工具报错时换一种方式（换工具或换参数）；连续失败两次就停下来说明卡在哪。
            - 用户一句话里有多件事时，全部处理完再回答。

            回答风格：
            - 直接给结果，两三句话说完，数据用工具返回的原文。
            - 工具返回里有 displayText 字段时，把它原样输出，不要改写其中的数字、id 和单号。
            - 不要复述这些规则，不要评价自己"符合要求"，不要输出核对过程或清单。
            """;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final LlmClient llmClient;
    private final AgentTools agentTools;
    private final StringRedisTemplate stringRedisTemplate;
    private final ThreadPoolTaskExecutor streamExecutor;

    public AiChatService(AiConversationMapper conversationMapper, AiMessageMapper messageMapper,
            LlmClient llmClient, AgentTools agentTools, StringRedisTemplate stringRedisTemplate,
            ThreadPoolTaskExecutor streamExecutor) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.llmClient = llmClient;
        this.agentTools = agentTools;
        this.stringRedisTemplate = stringRedisTemplate;
        this.streamExecutor = streamExecutor;
    }

    /**
     * 用户主动停止当前会话的 Agent 循环。
     *
     * 取消是一次性的：设置标志后，循环在下一轮开始时读到就停并删除标志。
     * TTL 5 分钟，避免用户没在跑任务时误设的标志长期残留。
     *
     * 注意粒度：只能在「轮次之间」生效，单次 LLM 调用（最长约 20 秒）打断不了。
     */
    public void cancel(Long userId, Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        stringRedisTemplate.opsForValue()
                .set(CANCEL_KEY_PREFIX + conversationId, "1", Duration.ofMinutes(5));
    }

    private boolean isCancelled(Long conversationId) {
        return "1".equals(stringRedisTemplate.opsForValue().get(CANCEL_KEY_PREFIX + conversationId));
    }

    /** 拼 system + 最近 N 条历史。同步和流式共用，避免两份逻辑漂移 */
    private List<Map<String, Object>> buildMessages(Long conversationId) {
        List<AiMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getId).last("LIMIT " + MAX_HISTORY));

        List<Map<String, Object>> llmMessages = new ArrayList<>();
        Map<String, Object> system = new HashMap<>();
        system.put("role", ROLE_SYSTEM);
        system.put("content", SYSTEM_PROMPT);
        llmMessages.add(system);

        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage m = history.get(i);
            Map<String, Object> message = new HashMap<>();
            message.put("role", m.getMessageRole());
            message.put("content", m.getContent());
            llmMessages.add(message);
        }
        return llmMessages;
    }

    @Transactional
    public ChatResponse chat(Long userId, ChatRequest request) {

        AiConversation conversation;
        if (request.conversationId() == null) {
            conversation = new AiConversation();
            conversation.setUserId(userId);
            conversation.setScene(SCENE_BUYER);
            conversation.setTitle(
                    request.message().length() <= 20 ? request.message() : request.message().substring(0, 20));
            conversationMapper.insert(conversation);
        } else {
            conversation = conversationMapper.selectById(request.conversationId());
            if (conversation == null || !userId.equals(conversation.getUserId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
            }
        }
        saveMessage(conversation.getId(), ROLE_USER, request.message());
        List<Map<String, Object>> llmMessages = buildMessages(conversation.getId());

        String reply = null;
        String lastError = null;
        int consecutiveFailures = 0;
        long deadline = System.currentTimeMillis() + TASK_TIME_BUDGET_MS;

        for (int round = 0; round < MAX_ROUNDS; round++) {

            // 护栏 1：时间预算。超时就把已有信息给用户，而不是让他继续等
            if (System.currentTimeMillis() > deadline) {
                reply = lastError != null
                        ? "这个问题比较绕，我先给你已经查到的部分，剩下的可以再问我。"
                        : "这个问题处理得有点久，先停一下。可以换个更具体的问法。";
                break;
            }

            // 护栏 5：用户主动停止。标志读一次就删 —— 取消是一次性的，
            // 留在 Redis 里会让用户下一句话一进来就被取消。
            if (isCancelled(conversation.getId())) {
                stringRedisTemplate.delete(CANCEL_KEY_PREFIX + conversation.getId());
                reply = "已停止。刚才完成的部分都已经保存，你可以继续说「继续」让我接着做。";
                break;
            }

            LlmClient.LlmResult result;
            try {
                result = llmClient.chatWithTools(llmMessages, agentTools.definitions(userId));
            } catch (Exception ex) {
                // 护栏 3：上游挂了也要给人话，不是 500
                log.warn("LLM 调用失败，降级返回", ex);
                reply = lastError != null
                        ? "模型服务暂时不可用。不过刚才已经查到一些结果，你可以先看看。"
                        : "我暂时连不上模型服务，请稍后再试。";
                break;
            }

            if (!result.hasToolCalls()) {
                // 工具失败后模型有时会编一句"已提交申请" —— 绝不能让它说出去。
                // consecutiveFailures > 0 表示仍有未恢复的失败；
                // 若后续重试成功，计数会清零，就不覆盖模型的正常回答。
                reply = consecutiveFailures > 0
                        ? "刚才的操作没有成功：审批单未生成，库存也没有变动。请稍后再试。"
                        : result.content();
                break;
            }

            llmMessages.add(result.rawMessage());

            for (LlmClient.ToolCall call : result.toolCalls()) {
                String output = agentTools.execute(userId, conversation.getId(),
                        call.name(), call.arguments());

                // 护栏 2：失败计数。工具返回的 error 是 JSON 里的 error 字段
                if (output.contains("\"error\"")) {
                    consecutiveFailures++;
                    lastError = output;
                    log.warn("工具调用失败：tool={}, round={}, failures={}",
                            call.name(), round, consecutiveFailures);
                } else {
                    consecutiveFailures = 0;
                }

                Map<String, Object> toolMessage = new HashMap<>();
                toolMessage.put("role", ROLE_TOOL);
                toolMessage.put("tool_call_id", call.id());
                toolMessage.put("content", output);
                llmMessages.add(toolMessage);
            }

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                reply = "这个操作连续失败了，我先停下来避免一直重试。"
                        + "你可以换个说法，或者稍后再试。";
                break;
            }

            if (!result.hasToolCalls()) {
                reply = result.content();
                break;
            }
        }

        if (reply == null) {
            reply = "抱歉，这个问题我暂时处理不了，请换个问法试试";
        }

        saveMessage(conversation.getId(), ROLE_ASSISTANT, reply);
        return new ChatResponse(conversation.getId(), reply);
    }

    /**
     * 流式对话：文字逐字推给前端，工具调用状态也实时推。
     *
     * 为什么用 SseEmitter：Agent 一轮要跑 10~30 秒，同步接口用户只能干等。
     * SSE 一边执行一边推，首字节几百毫秒就到 —— 用户的第一感受是"它没死"。
     *
     * 事件类型：
     *   start       会话已建立（带 conversationId）
     *   token       文字增量（前端直接 append）
     *   tool        正在调用哪个工具
     *   toolResult  该工具成功还是失败
     *   reply       最终完整回答（前端用它覆盖流式累积的内容，保证与库一致）
     *   error       出错
     */
    public SseEmitter chatStream(Long userId, ChatRequest request) {
        // 120 秒：比 TASK_TIME_BUDGET 长，留出网络和排队余量
        SseEmitter emitter = new SseEmitter(120_000L);

        streamExecutor.execute(() -> {
            try {
                doChatStream(userId, request, emitter);
                emitter.complete();
            } catch (Exception ex) {
                log.warn("流式对话失败", ex);
                sendEvent(emitter, "error", jsonText("message", "处理失败了，请稍后再试"));
                emitter.complete();
            }
        });

        return emitter;
    }

    private void doChatStream(Long userId, ChatRequest request, SseEmitter emitter) throws Exception {

        AiConversation conversation;
        if (request.conversationId() == null) {
            conversation = new AiConversation();
            conversation.setUserId(userId);
            conversation.setScene(SCENE_BUYER);
            conversation.setTitle(request.message().length() <= 20
                    ? request.message() : request.message().substring(0, 20));
            conversationMapper.insert(conversation);
        } else {
            conversation = conversationMapper.selectById(request.conversationId());
            if (conversation == null || !userId.equals(conversation.getUserId())) {
                sendEvent(emitter, "error", jsonText("message", "会话不存在"));
                return;
            }
        }

        Long convId = conversation.getId();
        sendEvent(emitter, "start", "{\"conversationId\":" + convId + "}");

        saveMessage(convId, ROLE_USER, request.message());
        List<Map<String, Object>> llmMessages = buildMessages(convId);

        String reply = null;
        String lastError = null;
        int consecutiveFailures = 0;
        long deadline = System.currentTimeMillis() + TASK_TIME_BUDGET_MS;

        for (int round = 0; round < MAX_ROUNDS; round++) {

            if (System.currentTimeMillis() > deadline) {
                reply = "处理得有点久，先停一下。可以换个更具体的问法。";
                break;
            }
            if (isCancelled(convId)) {
                stringRedisTemplate.delete(CANCEL_KEY_PREFIX + convId);
                reply = "已停止。刚才完成的部分都已经保存，你可以继续说「继续」。";
                break;
            }

            LlmClient.LlmResult result;
            try {
                // 文字增量即时推给前端；工具调用在 LlmClient 内部按 index 累加，流结束才返回
                result = llmClient.chatWithToolsStream(llmMessages,
                        agentTools.definitions(userId),
                        delta -> sendEvent(emitter, "token", jsonText("text", delta)));
            } catch (Exception ex) {
                log.warn("LLM 流式调用失败", ex);
                reply = lastError != null
                        ? "模型服务暂时不可用，先给你已经查到的部分。"
                        : "模型服务暂时不可用，请稍后再试。";
                break;
            }

            if (!result.hasToolCalls()) {
                // 工具失败后模型有时会编一句"已提交申请" —— 绝不能让它说出去。
                // consecutiveFailures > 0 表示仍有未恢复的失败；
                // 若后续重试成功，计数会清零，就不覆盖模型的正常回答。
                reply = consecutiveFailures > 0
                        ? "刚才的操作没有成功：审批单未生成，库存也没有变动。请稍后再试。"
                        : result.content();
                break;
            }

            llmMessages.add(result.rawMessage());

            for (LlmClient.ToolCall call : result.toolCalls()) {
                sendEvent(emitter, "tool", jsonText("name", call.name()));

                String output = agentTools.execute(userId, convId, call.name(), call.arguments());

                boolean ok = !output.contains("\"error\"");
                if (!ok) {
                    consecutiveFailures++;
                    lastError = output;
                    log.warn("流式工具失败：tool={}, round={}, failures={}",
                            call.name(), round, consecutiveFailures);
                } else {
                    consecutiveFailures = 0;
                }

                sendEvent(emitter, "toolResult",
                        "{\"name\":" + quote(call.name()) + ",\"ok\":" + ok + "}");

                Map<String, Object> toolMessage = new HashMap<>();
                toolMessage.put("role", ROLE_TOOL);
                toolMessage.put("tool_call_id", call.id());
                toolMessage.put("content", output);
                llmMessages.add(toolMessage);
            }

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                reply = "这个操作连续失败了，我先停下来避免一直重试。";
                break;
            }
        }

        if (reply == null) {
            reply = "抱歉，这个问题我暂时处理不了，请换个问法试试";
        }

        saveMessage(convId, ROLE_ASSISTANT, reply);
        sendEvent(emitter, "reply",
                "{\"conversationId\":" + convId + ",\"content\":" + quote(reply) + "}");
    }

    /** 推送一个 SSE 事件。客户端断开是常态，推不出去不该影响主流程 */
    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception ex) {
            log.debug("SSE 推送失败（客户端可能已断开）：{}", name);
        }
    }

    /** 手工拼 {"key":"value"}，避免为了一次序列化再注入一个 JsonMapper */
    private String jsonText(String key, String value) {
        return "{\"" + key + "\":" + quote(value == null ? "" : value) + "}";
    }

    /** 把字符串转成合法的 JSON 字符串字面量 */
    private String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private void saveMessage(Long conversationId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setMessageRole(role);
        message.setContent(content);
        messageMapper.insert(message);
    }
}
