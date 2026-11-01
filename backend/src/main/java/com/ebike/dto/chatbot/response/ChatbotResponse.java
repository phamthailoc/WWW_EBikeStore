package com.ebike.dto.chatbot.response;

import java.util.List;

public record ChatbotResponse(
    String answer,
    String matchedIntent,
    List<ChatbotRecommendationDto> recommendations
) {
}
