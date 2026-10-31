package com.ebike.chatbotModule.dto.request;

public record ChatbotAskRequest(
    String message,
    String content,
    String chatId
) {
    public String effectiveMessage() {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return content;
    }
}
