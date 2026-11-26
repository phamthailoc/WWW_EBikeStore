package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.admin.request.PricingRuleUpdateRequest;
import com.ebike.dto.admin.request.PromotionCreateRequest;
import com.ebike.dto.admin.request.PromotionUpdateRequest;
import com.ebike.model.entity.admin.PricingRule;
import com.ebike.model.entity.admin.Promotion;
import com.ebike.repository.admin.AdminAuditLogRepository;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.AuthenticationLogRepository;
import com.ebike.repository.auth.RoleRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.admin.impl.AdminServiceImpl;
import com.ebike.service.product.ProductReviewService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminServicePricingPromotionsTest {

    PricingRuleRepository pricing = mock(PricingRuleRepository.class);
    PromotionRepository promotions = mock(PromotionRepository.class);
    AuditLogService audit = mock(AuditLogService.class);
    AdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminServiceImpl(pricing, promotions, mock(AdminAuditLogRepository.class), mock(UserRepository.class),
            mock(RoleRepository.class), mock(PasswordEncoder.class), mock(OrderRepository.class), mock(AuthenticationLogRepository.class),
            mock(TestRideBookingRepository.class), mock(ProductReviewService.class), audit);
        when(pricing.save(any(PricingRule.class))).thenAnswer(i -> i.getArgument(0));
        when(promotions.save(any(Promotion.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(status.value());
    }

    private PricingRule rule(String code) {
        PricingRule r = new PricingRule();
        r.setCode(code);
        r.setName(code);
        r.setUnit("VND");
        r.setAmount(BigDecimal.ONE);
        r.setStatus("ACTIVE");
        ReflectionTestUtils.setField(r, "id", 1L);
        when(pricing.findById(1L)).thenReturn(Optional.of(r));
        return r;
    }

    private static PricingRuleUpdateRequest amt(String amount) {
        return new PricingRuleUpdateRequest(amount == null ? null : new BigDecimal(amount), null, null);
    }

    // ------------------------------------------------------------------ pricing rules

    @Test
    void pricing_update_setsAmountStatusAndDefaultsEffectiveFromToNow() {
        PricingRule r = rule("HOME_DELIVERY_SAME_CITY");
        var res = service.updatePricingRule(1L, new PricingRuleUpdateRequest(new BigDecimal("250000"), "inactive", null));
        assertThat(r.getAmount()).isEqualByComparingTo("250000");
        assertThat(r.getStatus()).isEqualTo("INACTIVE");
        assertThat(r.getEffectiveFrom()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(res.amount()).isEqualByComparingTo("250000");
        verify(audit).record("cập nhật biểu phí", "HOME_DELIVERY_SAME_CITY");
    }

    @Test
    void pricing_update_keepsGivenEffectiveFrom() {
        PricingRule r = rule("HOME_DELIVERY_SAME_CITY");
        LocalDateTime later = LocalDateTime.now().plusDays(3);
        service.updatePricingRule(1L, new PricingRuleUpdateRequest(BigDecimal.TEN, "SCHEDULED", later));
        assertThat(r.getEffectiveFrom()).isEqualTo(later);
        assertThat(r.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void pricing_update_unknownRule_is404() {
        assertThatThrownBy(() -> service.updatePricingRule(99L, amt("1"))).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void pricing_update_nullOrNegativeAmount_is400() {
        rule("HOME_DELIVERY_SAME_CITY");
        assertThatThrownBy(() -> service.updatePricingRule(1L, amt(null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updatePricingRule(1L, amt("-1"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(pricing, never()).save(any());
    }

    @Test
    void pricing_update_zeroIsAllowedForAFee_whenAdminWantsFreeDelivery() {
        PricingRule r = rule("HOME_DELIVERY_SAME_CITY");
        service.updatePricingRule(1L, amt("0"));
        assertThat(r.getAmount()).isEqualByComparingTo("0");
    }

    @Test
    void pricing_update_badStatus_is400() {
        rule("HOME_DELIVERY_SAME_CITY");
        assertThatThrownBy(() -> service.updatePricingRule(1L, new PricingRuleUpdateRequest(BigDecimal.TEN, "LIVE", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @CsvSource({"60,true", "60.00,true", "60.01,false", "61,false", "0,true", "1500000,false"})
    void pricing_installmentRate_capAtSixtyPercentPerYear(String amount, boolean ok) {
        rule("INSTALLMENT_RATE_12");
        if (ok) {
            service.updatePricingRule(1L, amt(amount));
        } else {
            assertThatThrownBy(() -> service.updatePricingRule(1L, amt(amount))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "SAVINGS_ELECTRICITY_PRICE,499,false", "SAVINGS_ELECTRICITY_PRICE,500,true", "SAVINGS_ELECTRICITY_PRICE,20000,true",
        "SAVINGS_ELECTRICITY_PRICE,20001,false", "SAVINGS_ELECTRICITY_PRICE,2.9,false", "SAVINGS_ELECTRICITY_PRICE,0,false",
        "SAVINGS_PETROL_PRICE,4999,false", "SAVINGS_PETROL_PRICE,5000,true", "SAVINGS_PETROL_PRICE,200000,true", "SAVINGS_PETROL_PRICE,200001,false",
        "SAVINGS_PETROL_USE_E_BIKE,0.49,false", "SAVINGS_PETROL_USE_E_BIKE,0.5,true", "SAVINGS_PETROL_USE_E_BIKE,10,true",
        "SAVINGS_PETROL_USE_E_BIKE,10.01,false", "SAVINGS_PETROL_USE_E_BIKE,1.855,false", "SAVINGS_PETROL_USE_E_BIKE,1.85,true",
        "SAVINGS_PETROL_USE_E_BIKE,1.850,true"
    })
    void pricing_savingsRules_floorCeilingAndTwoDecimals(String code, String amount, boolean ok) {
        rule(code);
        if (ok) {
            service.updatePricingRule(1L, amt(amount));
        } else {
            assertThatThrownBy(() -> service.updatePricingRule(1L, amt(amount))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        }
    }

    /** Live finding: REGISTRATION_SERVICE accepts amount 0. The default is 500,000 VND; a 0 fee while the service is "offered" silently gives it away. */
    @Test
    @Tag("bug")
    void bug_pricing_registrationServiceAcceptsZero() {
        rule("REGISTRATION_SERVICE");
        assertThatThrownBy(() -> service.updatePricingRule(1L, amt("0"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    /** Registration tax is a percentage "shown for reference"; 150% is a typo (an amount in dong) that customers would see. */
    @Test
    @Tag("bug")
    void bug_pricing_registrationTaxRateAbove100PercentAccepted() {
        rule("REGISTRATION_TAX_RATE_CENTRAL");
        assertThatThrownBy(() -> service.updatePricingRule(1L, amt("150"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    @Tag("bug")
    void bug_pricing_deliveryFeeHasNoUpperBound() {
        rule("HOME_DELIVERY_OTHER_PROVINCE");
        assertThatThrownBy(() -> service.updatePricingRule(1L, amt("999999999999"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    // ------------------------------------------------------------------ promotions

    private static PromotionCreateRequest promo(String code, String name, String type, String value, String max, Integer limit,
                                                String status, LocalDateTime from, LocalDateTime to) {
        return new PromotionCreateRequest(code, name, type, value == null ? null : new BigDecimal(value),
            max == null ? null : new BigDecimal(max), limit, status, from, to);
    }

    @Test
    void promo_create_happy_stripsWhitespaceInCode_defaultsPercentageAndWaiting() {
        var res = service.createPromotion(promo(" SAVE 10 ", " Summer ", null, "10", null, null, null, null, null));
        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(promotions).save(saved.capture());
        Promotion p = saved.getValue();
        assertThat(p.getCode()).isEqualTo("SAVE10");
        assertThat(p.getCampaignName()).isEqualTo("Summer");
        assertThat(p.getDiscountType()).isEqualTo("PERCENTAGE");
        assertThat(p.getStatus()).isEqualTo("WAITING");
        assertThat(p.getUsageCount()).isZero();
        assertThat(p.getUsageLimit()).isZero();
        assertThat(res.code()).isEqualTo("SAVE10");
        verify(audit).record("tạo campaign", "SAVE10");
    }

    @Test
    void promo_create_duplicateCode_is409() {
        when(promotions.existsByCode("SAVE10")).thenReturn(true);
        assertThatThrownBy(() -> service.createPromotion(promo("SAVE 10", "x", null, "10", null, null, null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(promotions, never()).save(any());
    }

    @Test
    void promo_create_blankCodeOrName_is400() {
        assertThatThrownBy(() -> service.createPromotion(promo("  ", "x", null, "10", null, null, null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createPromotion(promo("A", " ", null, "10", null, null, null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @CsvSource({"0,false", "-1,false", "0.01,true", "100,true", "100.01,false", "1000,false"})
    void promo_create_percentageValueBoundaries(String value, boolean ok) {
        if (ok) {
            service.createPromotion(promo("C" + value.hashCode(), "x", "PERCENTAGE", value, null, null, null, null, null));
        } else {
            assertThatThrownBy(() -> service.createPromotion(promo("C1", "x", "PERCENTAGE", value, null, null, null, null, null)))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void promo_create_fixedAmountMayExceed100() {
        service.createPromotion(promo("FIX", "x", "fixed_amount", "500000", null, null, null, null, null));
        verify(promotions).save(any(Promotion.class));
    }

    @Test
    void promo_create_nullValueIs400() {
        assertThatThrownBy(() -> service.createPromotion(promo("A", "x", null, null, null, null, null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void promo_create_unknownTypeOrStatus_is400() {
        assertThatThrownBy(() -> service.createPromotion(promo("A", "x", "BOGO", "10", null, null, null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createPromotion(promo("A", "x", null, "10", null, null, "ACTIVE", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void promo_create_endBeforeStart_is400_equalIsAccepted() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 0, 0);
        assertThatThrownBy(() -> service.createPromotion(promo("A", "x", null, "10", null, null, null, now, now.minusSeconds(1))))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        service.createPromotion(promo("B", "x", null, "10", null, null, null, now, now));
    }

    @Test
    void promo_create_negativeUsageLimitBecomesZeroUnlimited() {
        service.createPromotion(promo("A", "x", null, "10", null, -5, null, null, null));
        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(promotions).save(saved.capture());
        assertThat(saved.getValue().getUsageLimit()).isZero();
    }

    /**
     * OrderServiceImpl applies the cap as discount.min(maxDiscountAmount). update() treats a cap <= 0 as "no cap", but create() stores it
     * as typed: a cap of -50,000 turns the discount into a surcharge and 0 makes the code worthless. Expected: 400 or "no cap" (null).
     * AdminServiceImpl.java:142 (createPromotion) vs :170-173 (updatePromotion).
     */
    @Test
    @Tag("bug")
    void bug_promo_create_negativeMaxDiscountIsStoredAsIs() {
        try {
            service.createPromotion(promo("NEG", "x", "PERCENTAGE", "10", "-50000", null, null, null, null));
        } catch (ResponseStatusException refused) {
            return;
        }
        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(promotions).save(saved.capture());
        assertThat(saved.getValue().getMaxDiscountAmount()).isNull();
    }

    @Test
    @Tag("bug")
    void bug_promo_create_zeroMaxDiscountIsNotNormalisedLikeUpdateDoes() {
        try {
            service.createPromotion(promo("ZERO", "x", "PERCENTAGE", "10", "0", null, null, null, null));
        } catch (ResponseStatusException refused) {
            return;
        }
        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(promotions).save(saved.capture());
        assertThat(saved.getValue().getMaxDiscountAmount()).isNull();
    }

    private Promotion existing() {
        Promotion p = new Promotion();
        p.setCode("SAVE10");
        p.setCampaignName("Old");
        p.setDiscountType("PERCENTAGE");
        p.setDiscountValue(new BigDecimal("10"));
        p.setMaxDiscountAmount(new BigDecimal("100000"));
        p.setUsageLimit(5);
        p.setUsageCount(2);
        p.setStatus("LIVE");
        ReflectionTestUtils.setField(p, "id", 7L);
        when(promotions.findById(7L)).thenReturn(Optional.of(p));
        return p;
    }

    private static PromotionUpdateRequest upd(String name, String type, String value, String max, Integer limit, String status,
                                              LocalDateTime from, LocalDateTime to) {
        return new PromotionUpdateRequest(name, type, value == null ? null : new BigDecimal(value), max == null ? null : new BigDecimal(max),
            limit, status, from, to);
    }

    @Test
    void promo_update_onlyGivenFieldsChange_usageCountAndCodeUntouched() {
        Promotion p = existing();
        service.updatePromotion(7L, upd(" New ", null, "20", null, null, null, null, null));
        assertThat(p.getCampaignName()).isEqualTo("New");
        assertThat(p.getDiscountValue()).isEqualByComparingTo("20");
        assertThat(p.getCode()).isEqualTo("SAVE10");
        assertThat(p.getUsageCount()).isEqualTo(2);
        assertThat(p.getStatus()).isEqualTo("LIVE");
    }

    @Test
    void promo_update_unknown_is404() {
        assertThatThrownBy(() -> service.updatePromotion(1L, upd(null, null, null, null, null, null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void promo_update_zeroCapMeansNoCap_negativeLimitIgnored() {
        Promotion p = existing();
        service.updatePromotion(7L, upd(null, null, null, "0", -3, null, null, null));
        assertThat(p.getMaxDiscountAmount()).isNull();
        assertThat(p.getUsageLimit()).isEqualTo(5);
    }

    @Test
    void promo_update_switchToPercentageWithBigFixedValue_isRefused_valueUnchangedInMemoryRejectedBeforeSave() {
        Promotion p = existing();
        p.setDiscountType("FIXED_AMOUNT");
        p.setDiscountValue(new BigDecimal("500000"));
        assertThatThrownBy(() -> service.updatePromotion(7L, upd(null, "PERCENTAGE", null, null, null, null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(promotions, never()).save(any());
    }

    @Test
    void promo_update_zeroOrNegativeValue_is400() {
        existing();
        assertThatThrownBy(() -> service.updatePromotion(7L, upd(null, null, "0", null, null, null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updatePromotion(7L, upd(null, null, "-5", null, null, null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void promo_update_endBeforeExistingStart_is400() {
        Promotion p = existing();
        p.setStartsAt(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThatThrownBy(() -> service.updatePromotion(7L, upd(null, null, null, null, null, null, null, LocalDateTime.of(2026, 5, 1, 0, 0))))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void promo_update_expireAndDisable_statusesAccepted_garbageRejected() {
        Promotion p = existing();
        service.updatePromotion(7L, upd(null, null, null, null, null, "disabled", null, null));
        assertThat(p.getStatus()).isEqualTo("DISABLED");
        assertThatThrownBy(() -> service.updatePromotion(7L, upd(null, null, null, null, null, "PAUSED", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void promo_list_filtersByStatusCaseInsensitive() {
        Promotion a = existing();
        Promotion b = new Promotion();
        b.setCode("B");
        b.setStatus("EXPIRED");
        ReflectionTestUtils.setField(a, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.of(2026, 2, 1, 0, 0));
        when(promotions.findAll()).thenReturn(java.util.List.of(a, b));
        assertThat(service.getPromotions(" live ")).extracting(r -> r.code()).containsExactly("SAVE10");
        assertThat(service.getPromotions(null)).extracting(r -> r.code()).containsExactly("B", "SAVE10");
    }
}
