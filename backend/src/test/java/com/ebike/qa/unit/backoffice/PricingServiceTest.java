package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.admin.PricingRule;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.service.admin.PricingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0);
    PricingRuleRepository repo = mock(PricingRuleRepository.class);
    PricingService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new PricingService(repo, clock);
        when(repo.findByCode(anyString())).thenReturn(Optional.empty());
    }

    private PricingRule rule(String code, String amount, String status, LocalDateTime from) {
        PricingRule r = new PricingRule();
        r.setCode(code);
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        r.setStatus(status);
        r.setEffectiveFrom(from);
        when(repo.findByCode(code)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void missingRule_usesDefaults() {
        assertThat(service.registrationServicePerBike()).isEqualByComparingTo("500000");
        assertThat(service.homeDeliveryPerBike(true)).isEqualByComparingTo("200000");
        assertThat(service.homeDeliveryPerBike(false)).isEqualByComparingTo("800000");
        assertThat(service.registrationTaxRateCentral()).isEqualByComparingTo("5");
        assertThat(service.registrationTaxRateOther()).isEqualByComparingTo("2");
    }

    @Test
    void activeRuleInEffect_overridesDefault_includingNullEffectiveFrom() {
        rule(PricingService.HOME_DELIVERY_SAME_CITY, "123456.78", "ACTIVE", null);
        assertThat(service.homeDeliveryPerBike(true)).isEqualByComparingTo("123456.78");
    }

    @Test
    void ruleEffectiveExactlyNow_isInEffect_oneSecondLaterIsNot() {
        rule(PricingService.REGISTRATION_SERVICE, "700000", "ACTIVE", NOW);
        assertThat(service.registrationServicePerBike()).isEqualByComparingTo("700000");
        rule(PricingService.REGISTRATION_SERVICE, "700000", "ACTIVE", NOW.plusSeconds(1));
        assertThat(service.registrationServicePerBike()).isEqualByComparingTo("500000");
    }

    @Test
    void inactiveOrScheduledRule_fallsBackToDefault() {
        rule(PricingService.REGISTRATION_SERVICE, "1", "INACTIVE", null);
        assertThat(service.registrationServicePerBike()).isEqualByComparingTo("500000");
        rule(PricingService.REGISTRATION_SERVICE, "1", "SCHEDULED", null);
        assertThat(service.registrationServicePerBike()).isEqualByComparingTo("500000");
    }

    @Test
    void zeroAmountOfAnActiveRule_isHonouredForFees() {
        rule(PricingService.HOME_DELIVERY_SAME_CITY, "0", "ACTIVE", null);
        assertThat(service.homeDeliveryPerBike(true)).isEqualByComparingTo("0");
    }

    @Test
    void registrationServiceOffered_untilAdminSwitchesItOff() {
        assertThat(service.registrationServiceOffered()).isTrue();
        rule(PricingService.REGISTRATION_SERVICE, "1", "ACTIVE", null);
        assertThat(service.registrationServiceOffered()).isTrue();
        rule(PricingService.REGISTRATION_SERVICE, "1", "INACTIVE", null);
        assertThat(service.registrationServiceOffered()).isFalse();
        rule(PricingService.REGISTRATION_SERVICE, "1", "SCHEDULED", null);
        assertThat(service.registrationServiceOffered()).isTrue();
    }

    @Test
    void installmentRate_defaultsPerTerm() {
        assertThat(service.installmentRate(6)).isEqualByComparingTo("8");
        assertThat(service.installmentRate(9)).isEqualByComparingTo("9");
        assertThat(service.installmentRate(12)).isEqualByComparingTo("10");
        assertThat(PricingService.defaultInstallmentRate(24)).isEqualByComparingTo("10");
    }

    @Test
    void installmentTermOffered_missingRuleMeansOffered_inactiveOrFutureMeansOff() {
        assertThat(service.installmentTermOffered(6)).isTrue();
        rule("INSTALLMENT_RATE_6", "7", "INACTIVE", null);
        assertThat(service.installmentTermOffered(6)).isFalse();
        rule("INSTALLMENT_RATE_6", "7", "ACTIVE", NOW.plusDays(1));
        assertThat(service.installmentTermOffered(6)).isFalse();
        rule("INSTALLMENT_RATE_6", "7", "ACTIVE", NOW.minusDays(1));
        assertThat(service.installmentTermOffered(6)).isTrue();
    }

    @Test
    void offeredInstallmentRates_oneQuery_keepsOrder_dropsDisabledTerms() {
        PricingRule off = new PricingRule();
        off.setCode("INSTALLMENT_RATE_9");
        off.setStatus("INACTIVE");
        off.setAmount(new BigDecimal("12"));
        PricingRule custom = new PricingRule();
        custom.setCode("INSTALLMENT_RATE_12");
        custom.setStatus("ACTIVE");
        custom.setAmount(new BigDecimal("15.5"));
        when(repo.findByCodeIn(any())).thenReturn(List.of(off, custom));
        Map<Integer, BigDecimal> offered = service.offeredInstallmentRates(List.of(6, 9, 12));
        assertThat(offered.keySet()).containsExactly(6, 12);
        assertThat(offered.get(6)).isEqualByComparingTo("8");
        assertThat(offered.get(12)).isEqualByComparingTo("15.5");
    }

    @Test
    void savingsFigures_zeroNegativeOrNullAmount_fallBackToDefault() {
        rule(PricingService.SAVINGS_ELECTRICITY_PRICE, "0", "ACTIVE", null);
        assertThat(service.savingsElectricityPricePerKwh()).isEqualByComparingTo("2900");
        rule(PricingService.SAVINGS_PETROL_PRICE, "-5", "ACTIVE", null);
        assertThat(service.savingsPetrolPricePerLiter()).isEqualByComparingTo("22000");
        rule("SAVINGS_PETROL_USE_E_BIKE", null, "ACTIVE", null);
        assertThat(service.savingsPetrolLitersPer100Km("E_BIKE")).isEqualByComparingTo("1.8");
    }

    @Test
    void savingsFigures_positiveActiveValueIsUsed_andDefaultsPerVehicleType() {
        rule(PricingService.SAVINGS_ELECTRICITY_PRICE, "3100", "ACTIVE", null);
        assertThat(service.savingsElectricityPricePerKwh()).isEqualByComparingTo("3100");
        assertThat(service.savingsPetrolLitersPer100Km("E_SCOOTER")).isEqualByComparingTo("2.0");
        assertThat(service.savingsPetrolLitersPer100Km("E_MOTORBIKE")).isEqualByComparingTo("1.8");
    }
}
