package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.admin.request.PricingRuleUpdateRequest;
import com.ebike.model.entity.admin.PricingRule;
import com.ebike.repository.admin.AdminAuditLogRepository;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.RoleRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.service.admin.impl.AdminServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminPricingRuleTest {
    @Mock PricingRuleRepository pricingRules;
    @Mock PromotionRepository promotions;
    @Mock AdminAuditLogRepository audits;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder encoder;
    AdminServiceImpl service;
    PricingRule rule;

    @BeforeEach void setup() {
        service = new AdminServiceImpl(pricingRules, promotions, audits, users, roles, encoder);
        rule = new PricingRule();
        rule.setCode("REGISTRATION_SERVICE");
        rule.setName("Phí dịch vụ làm hồ sơ đăng ký xe");
        rule.setAmount(new BigDecimal("500000"));
        rule.setUnit("VND/xe");
    }

    @Test void adminCanSetFeeToZero() {
        when(pricingRules.findById(1L)).thenReturn(Optional.of(rule));
        when(pricingRules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.updatePricingRule(1L, new PricingRuleUpdateRequest(BigDecimal.ZERO, "ACTIVE", null));
        assertEquals(0, BigDecimal.ZERO.compareTo(rule.getAmount()));
    }

    @Test void negativeOrMissingAmountIsRejected() {
        when(pricingRules.findById(1L)).thenReturn(Optional.of(rule));
        assertThrows(ResponseStatusException.class,
            () -> service.updatePricingRule(1L, new PricingRuleUpdateRequest(new BigDecimal("-1"), "ACTIVE", null)));
        assertThrows(ResponseStatusException.class,
            () -> service.updatePricingRule(1L, new PricingRuleUpdateRequest(null, "ACTIVE", null)));
        verify(pricingRules, never()).save(any());
        assertEquals(0, new BigDecimal("500000").compareTo(rule.getAmount()));
    }
}
