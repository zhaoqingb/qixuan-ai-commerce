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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiChatService {

    private static final String SCENE_BUYER = "BUYER";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_TOOL = "tool";

    /** 只带最近 N 条历史，防止上下文无限增长把 token 撑爆 */
    private static final int MAX_HISTORY = 20;

    /** Agent 最多转几圈。没有上限的循环 = 无限烧钱 */
    private static final int MAX_ROUNDS = 5;

    private static final String SYSTEM_PROMPT = """
            你是「栖选商城」的购物助手，服务买家。
            你可以帮助用户：查询订单状态、查询商品价格与库存、解答售后问题。

            规则：
            1. 不能编造数据。不知道就明确说不知道。
            2. 涉及订单号、金额、库存时，必须以工具返回的真实数据为准。
            3. 回答简洁，用中文，不要长篇大论。
            """;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final LlmClient llmClient;
    private final AgentTools agentTools;

    public AiChatService(AiConversationMapper conversationMapper, AiMessageMapper messageMapper,
            LlmClient llmClient, AgentTools agentTools) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.llmClient = llmClient;
        this.agentTools = agentTools;
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
        List<AiMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversation.getId())
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

        String reply = null;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            LlmClient.LlmResult result = llmClient.chatWithTools(llmMessages,
                    agentTools.definitions(userId));
            if (!result.hasToolCalls()) {
                reply = result.content();
                break;
            }
            llmMessages.add(result.rawMessage());
            for (LlmClient.ToolCall call : result.toolCalls()) {
                String output = agentTools.execute(userId, conversation.getId(), call.name(),
                        call.arguments());

                Map<String, Object> toolMessage = new HashMap<>();
                toolMessage.put("role", ROLE_TOOL);
                toolMessage.put("tool_call_id", call.id());
                toolMessage.put("content", output);
                llmMessages.add(toolMessage);
            }
        }
        if (reply == null) {
            reply = "抱歉，这个问题我暂时处理不了，请换个问法试试";
        }

        saveMessage(conversation.getId(), ROLE_ASSISTANT, reply);
        return new ChatResponse(conversation.getId(), reply);
    }

    private void saveMessage(Long conversationId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setMessageRole(role);
        message.setContent(content);
        messageMapper.insert(message);
    }
}
