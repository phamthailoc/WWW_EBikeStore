package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.admin.PricingRule;
import com.ebike.repository.admin.PricingRuleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 10, 0);

    @Mock PricingRuleRepository rules;
    PricingService service;

    @BeforeEach void setup() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new PricingService(rules, clock);
    }

    private PricingRule rule(String code, String amount, String status, LocalDateTime from) {
        PricingRule rule = new PricingRule();
        rule.setCode(code);
        rule.setAmount(new BigDecimal(amount));
        rule.setStatus(status);
        rule.setEffectiveFrom(from);
        return rule;
    }

    @Test void activeRuleInEffectOverridesDefault() {
        when(rules.findByCode("REGISTRATION_SERVICE"))
            .thenReturn(Optional.of(rule("REGISTRATION_SERVICE", "750000", "ACTIVE", NOW.minusDays(1))));
        assertEquals(0, new BigDecimal("750000").compareTo(service.registrationServicePerBike()));
    }

    @Test void adminCanSetFeeToZero() {
        when(rules.findByCode("REGISTRATION_SERVICE"))
            .thenReturn(Optional.of(rule("REGISTRATION_SERVICE", "0", "ACTIVE", NOW.minusDays(1))));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.registrationServicePerBike()));
    }

    @Test void scheduledInactiveAndFutureRulesAreIgnored() {
        when(rules.findByCode("REGISTRATION_SERVICE"))
            .thenReturn(Optional.of(rule("REGISTRATION_SERVICE", "750000", "SCHEDULED", NOW.minusDays(1))));
        assertEquals(PricingService.DEFAULT_REGISTRATION_SERVICE, service.registrationServicePerBike());

        when(rules.findByCode("REGISTRATION_SERVICE"))
            .thenReturn(Optional.of(rule("REGISTRATION_SERVICE", "750000", "INACTIVE", NOW.minusDays(1))));
        assertEquals(PricingService.DEFAULT_REGISTRATION_SERVICE, service.registrationServicePerBike());

        when(rules.findByCode("REGISTRATION_SERVICE"))
            .thenReturn(Optional.of(rule("REGISTRATION_SERVICE", "750000", "ACTIVE", NOW.plusHours(1))));
        assertEquals(PricingService.DEFAULT_REGISTRATION_SERVICE, service.registrationServicePerBike());
    }

    @Test void missingRuleFallsBackToDefault() {
        when(rules.findByCode("REGISTRATION_SERVICE")).thenReturn(Optional.empty());
        assertEquals(PricingService.DEFAULT_REGISTRATION_SERVICE, service.registrationServicePerBike());
    }

    @Test void homeDeliveryUsesSameCityOrOtherProvinceRule() {
        when(rules.findByCode("HOME_DELIVERY_SAME_CITY"))
            .thenReturn(Optional.of(rule("HOME_DELIVERY_SAME_CITY", "150000", "ACTIVE", NOW.minusDays(1))));
        when(rules.findByCode("HOME_DELIVERY_OTHER_PROVINCE")).thenReturn(Optional.empty());
        assertEquals(0, new BigDecimal("150000").compareTo(service.homeDeliveryPerBike(true)));
        assertEquals(PricingService.DEFAULT_HOME_DELIVERY_OTHER_PROVINCE, service.homeDeliveryPerBike(false));
    }
}
