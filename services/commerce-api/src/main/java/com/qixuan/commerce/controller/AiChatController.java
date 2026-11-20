package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.ChatRequest;
import com.qixuan.commerce.dto.ChatResponse;
import com.qixuan.commerce.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestAttribute("userId") Long userId,
            @Valid @RequestBody ChatRequest request) {
        return aiChatService.chat(userId, request);
    }

    /**
     * 流式对话（SSE）。文字逐字推送，工具调用状态也实时推。
     * 前端用 fetch + ReadableStream 读取（EventSource 不支持 POST 和自定义头）。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestAttribute("userId") Long userId,
            @Valid @RequestBody ChatRequest request) {
        return aiChatService.chatStream(userId, request);
    }

    /**
     * 停止当前会话正在执行的 Agent 循环。
     * 粒度在「轮次之间」：当前这次 LLM 调用不会被立刻打断，但它结束后不会再进入下一轮。
     */
    @PostMapping("/conversations/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        aiChatService.cancel(userId, id);
    }
}
