package com.ebike.service.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.dto.chatbot.request.ChatbotAskRequest;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;
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
import org.springframework.context.i18n.LocaleContextHolder;

/** V33: a question about running costs gets the prices in force and the way to the calculator. */
class ChatbotSavingsTest {

    @AfterEach void resetLocale() { LocaleContextHolder.resetLocaleContext(); }

    private ChatbotService chatbot() {
        return chatbot(List.of());
    }

    private static Product product(String name, String slug, VehicleType type, String ah, String volts, String range, boolean featured) {
        Product product = new Product();
        product.setName(name);
        product.setSlug(slug);
        product.setActive(true);
        product.setFeatured(featured);
        product.setPrice(new BigDecimal("15000000"));
        ProductSpecification spec = new ProductSpecification();
        spec.setVehicleType(type);
        spec.setBatteryCapacityAh(new BigDecimal(ah));
        spec.setBatteryVoltageV(new BigDecimal(volts));
        spec.setMaxRangeKm(new BigDecimal(range));
        product.setSpecification(spec);
        return product;
    }

    private boolean isRunningCost(String question) {
        return "running_cost".equals(chatbot().debug(new ChatbotAskRequest(question, null, null)).matchedIntent());
    }

    private ChatbotService chatbot(List<Product> catalogue) {
        ProductRepository products = mock(ProductRepository.class);
        ShowroomRepository showrooms = mock(ShowroomRepository.class);
        PricingRuleRepository rules = mock(PricingRuleRepository.class);
        PdfKnowledgeBaseService pdf = mock(PdfKnowledgeBaseService.class);
        when(rules.findByCode(anyString())).thenReturn(Optional.empty());
        when(pdf.findRelevantContext(anyString())).thenReturn(PdfKnowledgeBaseService.PdfKnowledgeContext.empty());
        when(products.findAll()).thenReturn(catalogue);
        when(showrooms.findByActiveTrueOrderByDistrictAscNameAsc()).thenReturn(List.of());
        return new ChatbotService(products, showrooms, mock(GeminiChatClient.class), pdf, new PricingService(rules));
    }

    @Test void vietnameseQuestionAboutChargingCostPointsToTheTool() {
        var debug = chatbot().debug(new ChatbotAskRequest("Đi 20km mỗi ngày thì tiền sạc tiết kiệm được bao nhiêu?", null, null));
        assertEquals("running_cost", debug.matchedIntent());
        assertTrue(debug.fallbackAnswer().contains("Xe điện tiết kiệm bao nhiêu"), debug.fallbackAnswer());
        assertTrue(debug.fallbackAnswer().contains(String.format("%,.0f VND", new java.math.BigDecimal("22000"))), debug.fallbackAnswer());
    }

    @Test void otherWaysToAskAboutCostAreRecognised() {
        for (String question : List.of("Chi phí sạc một tháng là bao nhiêu?", "Chi phí nuôi xe điện mỗi tháng?", "Giá điện tính thế nào?",
            "Giá xăng bạn dùng so sánh là bao nhiêu?", "How much can I save in a year?", "What is the electricity cost per month?",
            "What is the fuel cost of an electric bike?", "Tiết kiệm bao nhiêu tiền xăng mỗi tháng?")) {
            assertTrue(isRunningCost(question), question);
        }
    }

    @Test void aSavingsWordInAQuestionAboutAPromotionIsNotACostQuestion() {
        for (String question : List.of("Mã khuyến mãi giúp tiết kiệm bao nhiêu?", "How much does the promo code save me?",
            "Tiết kiệm được gì khi mua xe tháng này?", "Is there a discount to save money?")) {
            assertFalse(isRunningCost(question), question);
        }
    }

    @Test void aNamedBikeGetsItsOwnFiguresAndItsCard() {
        Product amy = product("AMY", "amy", VehicleType.E_MOTORBIKE, "22", "48", "70", false);
        var response = chatbot(List.of(amy)).ask(new ChatbotAskRequest("Chi phí sạc của AMY mỗi tháng?", null, null));
        assertEquals("running_cost", response.matchedIntent());
        assertTrue(response.answer().contains("Với AMY, đi 20 km mỗi ngày và 26 ngày mỗi tháng"), response.answer());
        assertTrue(response.answer().contains("tiết kiệm khoảng"), response.answer());
        assertEquals(List.of("amy"), response.recommendations().stream().map(card -> card.slug()).toList());
    }

    @Test void withoutABikeNameTheAnswerLinksToSomeBikes() {
        Product battery = product("Pin 60V", "pin", VehicleType.BATTERY, "22", "60", "70", true);
        Product scooter = product("AMY", "amy", VehicleType.E_SCOOTER, "22", "60", "70", false);
        var response = chatbot(List.of(battery, scooter)).ask(new ChatbotAskRequest("Chi phí sạc mỗi tháng là bao nhiêu?", null, null));
        assertEquals("running_cost", response.matchedIntent());
        assertEquals(List.of("amy"), response.recommendations().stream().map(card -> card.slug()).toList());
    }

    @Test void aBatteryNamedInTheQuestionDoesNotGetTheCalculatorCard() {
        Product battery = product("Pin 60V", "pin-60v", VehicleType.BATTERY, "22", "60", "70", true);
        Product scooter = product("Tesla", "tesla", VehicleType.E_SCOOTER, "22", "60", "70", false);
        var response = chatbot(List.of(battery, scooter)).ask(new ChatbotAskRequest("Chi phí sạc của pin 60v?", null, null));
        assertEquals("running_cost", response.matchedIntent());
        assertEquals(List.of("tesla"), response.recommendations().stream().map(card -> card.slug()).toList());
    }

    @Test void anUnrelatedQuestionIsNotARunningCostQuestion() {
        var debug = chatbot().debug(new ChatbotAskRequest("Showroom ở quận 1 mở cửa mấy giờ?", null, null));
        assertFalse("running_cost".equals(debug.matchedIntent()));
    }
}
