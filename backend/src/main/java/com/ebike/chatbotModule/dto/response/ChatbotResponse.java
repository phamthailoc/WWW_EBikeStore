package com.ebike.chatbotModule.dto.response;

import java.util.List;

public record ChatbotResponse(
    String answer,
    String matchedIntent,
    List<ChatbotRecommendationDto> recommendations
) {
}
