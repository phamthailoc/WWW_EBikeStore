package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.admin.request.PromotionCreateRequest;
import com.ebike.dto.admin.request.PromotionUpdateRequest;
import com.ebike.model.entity.admin.Promotion;
import com.ebike.repository.admin.AdminAuditLogRepository;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.RoleRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.impl.AdminServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/** V6: a discount code the admin saves must make sense, and the admin can remove its cap. */
@ExtendWith(MockitoExtension.class)
class AdminPromotionRuleTest {
    @Mock PricingRuleRepository pricingRules;
    @Mock PromotionRepository promotions;
    @Mock AdminAuditLogRepository audits;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder encoder;
    AdminServiceImpl service;
    LocalDateTime now = LocalDateTime.of(2026, 11, 7, 10, 0);

    @BeforeEach void setup() {
        service = new AdminServiceImpl(pricingRules, promotions, audits, users, roles, encoder, mock(OrderRepository.class), mock(com.ebike.service.admin.AccountErasure.class));
    }

    private PromotionCreateRequest create(String type, String value, LocalDateTime start, LocalDateTime end) {
        return new PromotionCreateRequest("NEW10", "Khuyến mãi mới", type, new BigDecimal(value), null, 100, "LIVE", start, end);
    }

    @Test void aPercentageOverOneHundredIsRefused() {
        assertThrows(ResponseStatusException.class, () -> service.createPromotion(create("PERCENTAGE", "150", now, now.plusDays(5))));
        verify(promotions, never()).save(any());
    }

    @Test void aCodeCannotEndBeforeItStarts() {
        assertThrows(ResponseStatusException.class, () -> service.createPromotion(create("FIXED_AMOUNT", "50000", now, now.minusDays(1))));
        verify(promotions, never()).save(any());
    }

    @Test void aSensibleCodeIsSaved() {
        when(promotions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = service.createPromotion(create("PERCENTAGE", "15", now, now.plusDays(30)));
        assertEquals("NEW10", saved.code());
    }

    private Promotion existing() {
        Promotion promotion = new Promotion();
        promotion.setCode("OLD");
        promotion.setCampaignName("Cũ");
        promotion.setDiscountType("PERCENTAGE");
        promotion.setDiscountValue(new BigDecimal("10"));
        promotion.setMaxDiscountAmount(new BigDecimal("25000"));
        promotion.setUsageLimit(100);
        promotion.setStatus("LIVE");
        promotion.setStartsAt(now);
        promotion.setEndsAt(now.plusDays(10));
        when(promotions.findById(1L)).thenReturn(Optional.of(promotion));
        return promotion;
    }

    @Test void aZeroCapRemovesTheCapOnEdit() {
        Promotion promotion = existing();
        when(promotions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.updatePromotion(1L, new PromotionUpdateRequest(null, null, null, BigDecimal.ZERO, null, null, null, null));
        assertNull(promotion.getMaxDiscountAmount());
    }

    @Test void editsAreCheckedAgainstTheWholeCode() {
        Promotion promotion = existing();
        // 120 is fine as a fixed amount but not as a percentage; the type stays PERCENTAGE here
        assertThrows(ResponseStatusException.class,
            () -> service.updatePromotion(1L, new PromotionUpdateRequest(null, null, new BigDecimal("120"), null, null, null, null, null)));
        // moving the end date before the start date is refused
        assertThrows(ResponseStatusException.class,
            () -> service.updatePromotion(1L, new PromotionUpdateRequest(null, null, null, null, null, null, null, now.minusDays(2))));
        verify(promotions, never()).save(any());
    }

    @Test void switchingToAFixedAmountAllowsALargeValue() {
        Promotion promotion = existing();
        when(promotions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.updatePromotion(1L, new PromotionUpdateRequest(null, "FIXED_AMOUNT", new BigDecimal("120000"), null, null, null, null, null));
        assertEquals(new BigDecimal("120000"), promotion.getDiscountValue());
    }
}
