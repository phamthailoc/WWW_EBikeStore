package com.ebike.dto.chatbot.request;

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
