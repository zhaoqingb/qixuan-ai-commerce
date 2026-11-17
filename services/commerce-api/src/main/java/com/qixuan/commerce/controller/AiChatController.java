package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.ChatRequest;
import com.qixuan.commerce.dto.ChatResponse;
import com.qixuan.commerce.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
