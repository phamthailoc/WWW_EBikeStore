package com.ebike.chatbotModule.controller;

import com.ebike.chatbotModule.dto.request.ChatbotAskRequest;
import com.ebike.chatbotModule.dto.response.ChatbotDebugResponse;
import com.ebike.chatbotModule.dto.response.ChatbotResponse;
import com.ebike.chatbotModule.service.ChatbotService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/ask")
    public ChatbotResponse ask(@RequestBody ChatbotAskRequest request) {
        return chatbotService.ask(request);
    }

    @PostMapping("/debug")
    public ChatbotDebugResponse debug(@RequestBody ChatbotAskRequest request) {
        return chatbotService.debug(request);
    }
}
