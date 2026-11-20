package com.ebike.service.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;

/** V19: the FAQ and the chatbot answer in the language the visitor chose. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatbotEnglishTest {
    @Mock ProductRepository products;
    @Mock ShowroomRepository showrooms;
    @Mock GeminiChatClient gemini;
    @Mock PdfKnowledgeBaseService pdf;
    @Mock PricingRuleRepository pricingRules;

    private ChatbotService chatbot() {
        PricingRule rule = new PricingRule();
        rule.setCode(PricingService.REGISTRATION_SERVICE);
        rule.setAmount(new BigDecimal("650000"));
        rule.setStatus("ACTIVE");
        when(pricingRules.findByCode(PricingService.REGISTRATION_SERVICE)).thenReturn(Optional.of(rule));
        when(pdf.findRelevantContext(anyString())).thenReturn(PdfKnowledgeBaseService.PdfKnowledgeContext.empty());
        when(products.findAll()).thenReturn(List.of());
        when(showrooms.findByActiveTrueOrderByDistrictAscNameAsc()).thenReturn(List.of());
        return new ChatbotService(products, showrooms, gemini, pdf, new PricingService(pricingRules));
    }

    @AfterEach void resetLocale() { LocaleContextHolder.resetLocaleContext(); }

    @Test void theFaqListIsInEnglishWithTheCurrentFeesFilledIn() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var faq = chatbot().listFaq();
        assertEquals(12, faq.size());
        assertTrue(faq.stream().anyMatch(item -> item.question().equals("How much is the registration fee?")));
        String fee = faq.stream().filter(item -> item.question().contains("registration fee")).findFirst().orElseThrow().answer();
        assertTrue(fee.contains(String.format("%,.0f VND", new BigDecimal("650000"))), fee);
        assertFalse(fee.contains("{"), fee);
    }

    @Test void theFaqListStaysVietnameseByDefault() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        var faq = chatbot().listFaq();
        assertEquals(12, faq.size());
        assertTrue(faq.stream().anyMatch(item -> item.question().equals("Phí đăng ký là bao nhiêu?")));
        // the ordering answer holds commas: it must come through whole, not cut at the first comma
        String ordering = faq.stream().filter(item -> item.question().equals("Đặt hàng cần thông tin gì?")).findFirst().orElseThrow().answer();
        assertTrue(ordering.contains("Hệ thống không yêu cầu CCCD"), ordering);
    }

    @Test void anEnglishQuestionGetsAnEnglishAnswer() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String answer = chatbot().debug(new ChatbotAskRequest("How much is the registration fee?", null, null)).fallbackAnswer();
        assertTrue(answer.contains("licence plate") && answer.contains(String.format("%,.0f VND", new BigDecimal("650000"))), answer);
        assertFalse(answer.contains("Cửa hàng"), answer);
    }

    @Test void anUnknownEnglishQuestionGetsTheEnglishFallback() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String answer = chatbot().debug(new ChatbotAskRequest("tell me a joke", null, null)).fallbackAnswer();
        assertTrue(answer.startsWith("I can advise on electric bikes"), answer);
    }

    @Test void aVietnameseQuestionStillGetsAVietnameseAnswerOnTheEnglishSite() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String answer = chatbot().debug(new ChatbotAskRequest("Phí đăng ký là bao nhiêu?", null, null)).fallbackAnswer();
        assertTrue(answer.contains("Cửa hàng hỗ trợ đăng ký biển số"), answer);
    }

    @Test void csvLinesWithQuotedCommasAreKeptWhole() {
        assertEquals(List.of("7", "Đặt hàng", "a, b, c", "x\"y", "k"), ChatbotService.splitCsvLine("7,Đặt hàng,\"a, b, c\",\"x\"\"y\",k"));
    }
}
