package com.ebike.controller.chatbot;

import com.ebike.dto.chatbot.request.ChatbotAskRequest;
import com.ebike.dto.chatbot.response.ChatbotDebugResponse;
import com.ebike.dto.chatbot.response.ChatbotResponse;
import com.ebike.service.chatbot.ChatbotService;
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
