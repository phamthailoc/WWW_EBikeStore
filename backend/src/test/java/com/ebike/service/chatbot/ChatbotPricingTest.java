package com.ebike.service.chatbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.ebike.dto.chatbot.request.ChatbotAskRequest;
import com.ebike.model.entity.admin.PricingRule;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.admin.PricingService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** The chatbot must quote the registration service fee the admin set, not an old constant. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatbotPricingTest {
    @Mock ProductRepository products;
    @Mock ShowroomRepository showrooms;
    @Mock GeminiChatClient gemini;
    @Mock PdfKnowledgeBaseService pdf;
    @Mock PricingRuleRepository pricingRules;

    private ChatbotService chatbotWithRegistrationFee(String amount) {
        PricingRule rule = new PricingRule();
        rule.setCode(PricingService.REGISTRATION_SERVICE);
        rule.setAmount(new BigDecimal(amount));
        rule.setStatus("ACTIVE");
        when(pricingRules.findByCode(PricingService.REGISTRATION_SERVICE)).thenReturn(Optional.of(rule));
        when(pdf.findRelevantContext(anyString())).thenReturn(PdfKnowledgeBaseService.PdfKnowledgeContext.empty());
        return new ChatbotService(products, showrooms, gemini, pdf, new PricingService(pricingRules));
    }

    @Test
    void orderContextQuotesRegistrationFeeFromPricingRules() {
        ChatbotService chatbot = chatbotWithRegistrationFee("650000");

        String context = chatbot.debug(new ChatbotAskRequest("Đặt hàng có mất phí đăng ký không?", null, null)).orderPaymentContext();

        assertTrue(context.contains(String.format("%,.0f VND", new BigDecimal("650000"))), context);
        assertFalse(context.contains(String.format("%,.0f VND", new BigDecimal("2500000"))), context);
        assertFalse(context.contains("ưu đãi showroom"), "checkout has no showroom incentive: " + context);
    }

    @Test
    void faqAnswerQuotesRegistrationFeeFromPricingRules() {
        ChatbotService chatbot = chatbotWithRegistrationFee("650000");

        String answer = chatbot.debug(new ChatbotAskRequest("Phí đăng ký là bao nhiêu?", null, null)).fallbackAnswer();

        assertTrue(answer.contains(String.format("%,.0f VND", new BigDecimal("650000"))), answer);
        assertFalse(answer.contains("2500000"), answer);
        assertFalse(answer.contains("{"), "placeholder must be filled in: " + answer);
        assertFalse(answer.contains("ưu đãi showroom"), "checkout has no showroom incentive: " + answer);
    }
}
