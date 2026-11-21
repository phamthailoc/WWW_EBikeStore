package com.ebike.service.installment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.installment.InstallmentRequest;
import com.ebike.model.entity.admin.PricingRule;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.service.admin.PricingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** V24: what checkout needs from the store's instalment offer: the rates, the quote and the checking of an application. */
class InstallmentPlannerTest {

    private PricingRuleRepository rules;
    private InstallmentApplicationRepository applications;
    private UserRepository users;
    private InstallmentPlanner planner;
    private User customer;

    @BeforeEach
    void setUp() {
        rules = mock(PricingRuleRepository.class);
        applications = mock(InstallmentApplicationRepository.class);
        users = mock(UserRepository.class);
        planner = new InstallmentPlanner(new PricingService(rules), applications, users,
            new BigDecimal("5000000"), new BigDecimal("300000000"), new BigDecimal("3000000"), new BigDecimal("1000000000"), 60, 2);
        customer = new User();
        customer.setId(7L);
        when(applications.save(any(InstallmentApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void rate(String code, String amount, String status, LocalDateTime from) {
        PricingRule rule = new PricingRule();
        rule.setCode(code);
        rule.setAmount(new BigDecimal(amount));
        rule.setStatus(status);
        rule.setEffectiveFrom(from);
        when(rules.findByCode(code)).thenReturn(Optional.of(rule));
    }

    private static InstallmentRequest application(String id, String income, Integer percent, Integer term) {
        return new InstallmentRequest(id, income == null ? null : new BigDecimal(income), percent, term);
    }

    private static ResponseStatusException refused(Runnable action) {
        return assertThrows(ResponseStatusException.class, action::run);
    }

    // ---- the offer ----

    @Test
    void withoutRulesTheStoreOffersThreeTermsAtTheDefaultRates() {
        var terms = planner.terms();
        assertEquals(3, terms.size());
        assertEquals(6, terms.get(0).termMonths());
        assertEquals(0, terms.get(0).annualRatePercent().compareTo(new BigDecimal("8")));
        assertEquals(0, terms.get(1).annualRatePercent().compareTo(new BigDecimal("9")));
        assertEquals(0, terms.get(2).annualRatePercent().compareTo(new BigDecimal("10")));
    }

    @Test
    void anAdminEditedRateIsUsedAndAnInactiveTermIsNotOffered() {
        rate("INSTALLMENT_RATE_6", "5.5", "ACTIVE", LocalDateTime.now().minusDays(1));
        rate("INSTALLMENT_RATE_9", "9", "INACTIVE", LocalDateTime.now().minusDays(1));
        var terms = planner.terms();
        assertEquals(2, terms.size());
        assertEquals(0, terms.get(0).annualRatePercent().compareTo(new BigDecimal("5.5")));
        assertEquals(12, terms.get(1).termMonths());
    }

    @Test
    void aTermWhoseRateIsNotInEffectYetIsNotOffered() {
        rate("INSTALLMENT_RATE_12", "20", "ACTIVE", LocalDateTime.now().plusDays(3));
        rate("INSTALLMENT_RATE_9", "9", "SCHEDULED", LocalDateTime.now().minusDays(1));
        var terms = planner.terms();
        assertEquals(1, terms.size());
        assertEquals(6, terms.get(0).termMonths());
    }

    @Test
    void theConfigCarriesTheLimitsTheFormsShow() {
        var config = planner.config();
        assertEquals(20, config.minDownPaymentPercent());
        assertEquals(50, config.maxDownPaymentPercent());
        assertEquals(60, config.maxPaymentPercentOfIncome());
        assertEquals(2, config.maxOpenApplications());
        // a rejection note is part of the cancellation reason, so it has room for the prefix less than an approval note
        assertEquals(1000, config.maxApprovalNoteLength());
        assertEquals(1000 - InstallmentService.REJECTED_REASON_PREFIX.length(), config.maxRejectionNoteLength());
    }

    // ---- the quote ----

    @Test
    void aQuoteUsesTheCurrentRateAndSaysTheOrderCanBeFinanced() {
        var quote = planner.quote(new BigDecimal("30000000"), 30, 12);
        assertEquals(0, quote.monthlyPayment().compareTo(new BigDecimal("1925000")));
        assertTrue(quote.eligible());
        assertNull(quote.reason());
    }

    @Test
    void aSmallAmountIsStillPricedButMarkedNotEligible() {
        var quote = planner.quote(new BigDecimal("4000000"), 20, 6);
        assertFalse(quote.eligible());
        assertTrue(quote.reason().contains("5.000.000"));
        assertTrue(quote.monthlyPayment().signum() > 0);
    }

    @Test
    void aQuoteRefusesAnUnknownTermAnInactiveTermAndAWrongDownPayment() {
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("30000000"), 30, 24)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("30000000"), 10, 12)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("30000000"), 60, 12)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("30000000"), null, 12)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("30000000"), 30, null)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(BigDecimal.ZERO, 30, 12)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("-5"), 30, 12)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(null, 30, 12)).getStatusCode());
        // numbers in exponent form would make the arithmetic build values of millions of digits: refused before any computation
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("1e20000000"), 30, 12)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("1e-20000000"), 30, 12)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("2000000000000"), 30, 12)).getStatusCode());
        rate("INSTALLMENT_RATE_12", "10", "INACTIVE", LocalDateTime.now().minusDays(1));
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.quote(new BigDecimal("30000000"), 30, 12)).getStatusCode());
    }

    // ---- an application with an order ----

    @Test
    void aGoodApplicationIsPricedAndItsIdNumberIsMasked() {
        var prepared = planner.prepare(customer, new BigDecimal("30000000"), application("079 123 456 789", "20000000", 30, 12));
        assertEquals("********6789", prepared.idNumberMasked());
        assertEquals(0, prepared.plan().monthlyPayment().compareTo(new BigDecimal("1925000")));
        assertEquals(0, prepared.plan().downPayment().compareTo(new BigDecimal("9000000")));
        // the customer's row is locked before the open applications are counted
        verify(users).findLockedById(7L);
    }

    @Test
    void recordingKeepsTheSnapshotOfThePlanAndNeverTheFullIdNumber() {
        var prepared = planner.prepare(customer, new BigDecimal("30000000"), application("079123456789", "20000000", 30, 12));
        Order order = new Order();
        InstallmentApplication saved = planner.record(order, customer, prepared);
        assertEquals("********6789", saved.getIdNumberMasked());
        assertEquals(InstallmentApplication.PENDING, saved.getStatus());
        assertEquals(0, saved.getDownPaymentAmount().compareTo(new BigDecimal("9000000")));
        assertEquals(0, saved.getFinancedAmount().compareTo(new BigDecimal("21000000")));
        assertEquals(12, saved.getTermMonths());
        assertEquals(0, saved.getAnnualRatePercent().compareTo(new BigDecimal("10")));
        assertEquals(0, saved.getMonthlyPayment().compareTo(new BigDecimal("1925000")));
        assertEquals(0, saved.getTotalPayable().compareTo(new BigDecimal("32100000")));
        assertEquals(order, saved.getOrder());
        assertEquals(customer, saved.getUser());
    }

    @Test
    void aVisitorIsToldToSignIn() {
        assertEquals(HttpStatus.UNAUTHORIZED, refused(() -> planner.prepare(null, new BigDecimal("30000000"), application("079123456789", "20000000", 30, 12))).getStatusCode());
    }

    @Test
    void aMissingApplicationIsRefused() {
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.prepare(customer, new BigDecimal("30000000"), null)).getStatusCode());
    }

    @Test
    void anOrderOutsideTheFinancingRangeIsRefused() {
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.prepare(customer, new BigDecimal("4999999"), application("079123456789", "20000000", 30, 12))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.prepare(customer, new BigDecimal("300000001"), application("079123456789", "900000000", 30, 12))).getStatusCode());
    }

    @Test
    void aBadIdNumberIsRefusedWithoutEchoingIt() {
        ResponseStatusException error = refused(() -> planner.prepare(customer, new BigDecimal("30000000"), application("12345", "20000000", 30, 12)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertFalse(String.valueOf(error.getReason()).contains("12345"));
    }

    @Test
    void anIncomeOutsideItsRangeIsRefused() {
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.prepare(customer, new BigDecimal("30000000"), application("079123456789", null, 30, 12))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.prepare(customer, new BigDecimal("30000000"), application("079123456789", "2999999", 30, 12))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.prepare(customer, new BigDecimal("30000000"), application("079123456789", "1000000001", 30, 12))).getStatusCode());
    }

    @Test
    void aPaymentOverSixtyPercentOfTheIncomeIsRefused() {
        // 1,925,000 a month needs an income of at least 3,208,334
        assertEquals(HttpStatus.BAD_REQUEST, refused(() -> planner.prepare(customer, new BigDecimal("30000000"), application("079123456789", "3200000", 30, 12))).getStatusCode());
        assertEquals(0, planner.prepare(customer, new BigDecimal("30000000"), application("079123456789", "3300000", 30, 12)).plan().monthlyPayment().compareTo(new BigDecimal("1925000")));
    }

    @Test
    void aCustomerWithTheMaximumOfOpenApplicationsCannotAddAnother() {
        when(applications.countOpenOf(7L)).thenReturn(2L);
        ResponseStatusException error = refused(() -> planner.prepare(customer, new BigDecimal("30000000"), application("079123456789", "20000000", 30, 12)));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        when(applications.countOpenOf(7L)).thenReturn(1L);
        assertEquals("********6789", planner.prepare(customer, new BigDecimal("30000000"), application("079123456789", "20000000", 30, 12)).idNumberMasked());
    }
}
