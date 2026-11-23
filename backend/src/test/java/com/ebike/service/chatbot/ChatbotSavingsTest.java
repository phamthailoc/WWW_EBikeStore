package com.ebike.service.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.dto.chatbot.request.ChatbotAskRequest;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.admin.PricingService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

/** V33: a question about running costs gets the prices in force and the way to the calculator. */
class ChatbotSavingsTest {

    @AfterEach void resetLocale() { LocaleContextHolder.resetLocaleContext(); }

    private ChatbotService chatbot() {
        ProductRepository products = mock(ProductRepository.class);
        ShowroomRepository showrooms = mock(ShowroomRepository.class);
        PricingRuleRepository rules = mock(PricingRuleRepository.class);
        PdfKnowledgeBaseService pdf = mock(PdfKnowledgeBaseService.class);
        when(rules.findByCode(anyString())).thenReturn(Optional.empty());
        when(pdf.findRelevantContext(anyString())).thenReturn(PdfKnowledgeBaseService.PdfKnowledgeContext.empty());
        when(products.findAll()).thenReturn(List.of());
        when(showrooms.findByActiveTrueOrderByDistrictAscNameAsc()).thenReturn(List.of());
        return new ChatbotService(products, showrooms, mock(GeminiChatClient.class), pdf, new PricingService(rules));
    }

    @Test void vietnameseQuestionAboutChargingCostPointsToTheTool() {
        var debug = chatbot().debug(new ChatbotAskRequest("Đi 20km mỗi ngày thì tiền sạc tiết kiệm được bao nhiêu?", null, null));
        assertEquals("running_cost", debug.matchedIntent());
        assertTrue(debug.fallbackAnswer().contains("Xe điện tiết kiệm bao nhiêu"), debug.fallbackAnswer());
        assertTrue(debug.fallbackAnswer().contains(String.format("%,.0f VND", new java.math.BigDecimal("22000"))), debug.fallbackAnswer());
    }

    @Test void englishQuestionGetsAnEnglishAnswer() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var debug = chatbot().debug(new ChatbotAskRequest("How much would I save compared to petrol?", null, null));
        assertEquals("running_cost", debug.matchedIntent());
        assertTrue(debug.fallbackAnswer().contains("How much does an electric bike save?"), debug.fallbackAnswer());
    }

    @Test void anUnrelatedQuestionIsNotARunningCostQuestion() {
        var debug = chatbot().debug(new ChatbotAskRequest("Showroom ở quận 1 mở cửa mấy giờ?", null, null));
        assertFalse("running_cost".equals(debug.matchedIntent()));
    }
}
