package com.ebike.dto.chatbot.response;

import java.math.BigDecimal;

public record ChatbotRecommendationDto(
    Long id,
    String name,
    String slug,
    BigDecimal price,
    String reason
) {
}
