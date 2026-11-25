package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.admin.request.PricingRuleUpdateRequest;
import com.ebike.model.entity.admin.PricingRule;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.service.admin.impl.AdminServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** V33: an admin cannot type a zero or an absurd value into a rule of the savings calculator. */
class AdminSavingsRuleTest {
    PricingRuleRepository pricingRules = mock(PricingRuleRepository.class);
    AdminServiceImpl service;

    @BeforeEach void setup() {
        service = new AdminServiceImpl(pricingRules, mock(com.ebike.repository.admin.PromotionRepository.class), mock(com.ebike.repository.admin.AdminAuditLogRepository.class),
            mock(com.ebike.repository.auth.UserRepository.class), mock(com.ebike.repository.auth.RoleRepository.class), mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            mock(com.ebike.repository.order.OrderRepository.class), mock(com.ebike.repository.auth.AuthenticationLogRepository.class), mock(com.ebike.service.admin.AuditLogService.class));
    }

    private PricingRule rule(String code) {
        PricingRule rule = new PricingRule();
        rule.setCode(code);
        rule.setName("x");
        rule.setUnit("VND/kWh");
        rule.setAmount(new BigDecimal("2900"));
        when(pricingRules.findById(1L)).thenReturn(Optional.of(rule));
        when(pricingRules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return rule;
    }

    @Test void zeroAndTooLargeValuesAreRefused() {
        PricingRule rule = rule(PricingService.SAVINGS_ELECTRICITY_PRICE);
        assertThrows(ResponseStatusException.class, () -> service.updatePricingRule(1L, new PricingRuleUpdateRequest(BigDecimal.ZERO, "ACTIVE", null)));
        assertThrows(ResponseStatusException.class, () -> service.updatePricingRule(1L, new PricingRuleUpdateRequest(new BigDecimal("20001"), "ACTIVE", null)));
        // 2.900 typed with a thousands dot arrives as 2.9: refused, not shown to every visitor as 2.9 dong
        assertThrows(ResponseStatusException.class, () -> service.updatePricingRule(1L, new PricingRuleUpdateRequest(new BigDecimal("2.9"), "ACTIVE", null)));
        verify(pricingRules, never()).save(any());
        assertEquals(0, new BigDecimal("2900").compareTo(rule.getAmount()));
    }

    @Test void consumptionHasItsOwnCeiling() {
        rule(PricingService.SAVINGS_PETROL_USE_PREFIX + "E_BIKE");
        assertThrows(ResponseStatusException.class, () -> service.updatePricingRule(1L, new PricingRuleUpdateRequest(new BigDecimal("11"), "ACTIVE", null)));
    }

    @Test void aSensibleValueIsSaved() {
        PricingRule rule = rule(PricingService.SAVINGS_ELECTRICITY_PRICE);
        service.updatePricingRule(1L, new PricingRuleUpdateRequest(new BigDecimal("3200"), "ACTIVE", null));
        assertEquals(0, new BigDecimal("3200").compareTo(rule.getAmount()));
    }
}
