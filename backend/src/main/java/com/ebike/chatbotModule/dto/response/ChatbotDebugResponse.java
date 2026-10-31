package com.ebike.chatbotModule.dto.response;

import java.util.List;

public record ChatbotDebugResponse(
    String message,
    String normalizedMessage,
    String matchedFaqQuestion,
    String matchedFaqCategory,
    String matchedIntent,
    boolean geminiConfigured,
    String showroomContext,
    String orderPaymentContext,
    String pdfKnowledgeContext,
    List<String> pdfKnowledgeSources,
    String fallbackAnswer,
    List<ChatbotRecommendationDto> recommendations
) {
}
