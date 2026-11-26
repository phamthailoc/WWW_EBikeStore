package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.installment.InstallmentConfigResponse;
import com.ebike.dto.installment.InstallmentQuoteResponse;
import com.ebike.dto.installment.InstallmentRequest;
import com.ebike.dto.installment.InstallmentTermResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.service.admin.PricingService;
import com.ebike.service.installment.InstallmentPlanner;
import com.ebike.service.installment.InstallmentPlanner.Prepared;
import com.ebike.service.installment.InstallmentService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstallmentPlannerTest {

    @Mock PricingService pricing;
    @Mock InstallmentApplicationRepository applications;
    @Mock UserRepository users;

    User user = Fx.user(7L, "an");
    InstallmentPlanner planner;

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @BeforeEach
    void setUp() {
        planner = new InstallmentPlanner(pricing, applications, users, d("5000000"), d("300000000"), d("3000000"), d("1000000000"), 60, 2);
        Map<Integer, BigDecimal> offered = new LinkedHashMap<>();
        offered.put(6, d("12"));
        offered.put(9, d("14"));
        offered.put(12, d("16"));
        when(pricing.offeredInstallmentRates(any())).thenReturn(offered);
        when(pricing.installmentTermOffered(anyInt())).thenReturn(true);
        when(pricing.installmentRate(6)).thenReturn(d("12"));
        when(pricing.installmentRate(9)).thenReturn(d("14"));
        when(pricing.installmentRate(12)).thenReturn(d("16"));
        when(applications.save(any(InstallmentApplication.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    ResponseStatusException refused(Runnable call) {
        Throwable t = catchThrowable(call::run);
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        return (ResponseStatusException) t;
    }

    static InstallmentRequest request(String id, String income, Integer percent, Integer term) {
        return new InstallmentRequest(id, income == null ? null : d(income), percent, term);
    }

    // ------------------------------------------------------------------ startup validation of the settings

    @ParameterizedTest
    @CsvSource({
        "0,300000000,3000000,1000000000,60,2,min-total",
        "-1,300000000,3000000,1000000000,60,2,min-total",
        "300000001,300000000,3000000,1000000000,60,2,min-total",
        "5000000,300000000,0,1000000000,60,2,min-income",
        "5000000,300000000,1000000001,1000000000,60,2,min-income",
        "5000000,300000000,3000000,1000000000,0,2,max-payment-percent-of-income",
        "5000000,300000000,3000000,1000000000,101,2,max-payment-percent-of-income",
        "5000000,300000000,3000000,1000000000,60,0,max-open-per-customer"})
    void aWrongSettingStopsTheApplication(String minTotal, String maxTotal, String minIncome, String maxIncome, int percent, int open, String setting) {
        assertThatThrownBy(() -> new InstallmentPlanner(pricing, applications, users, d(minTotal), d(maxTotal), d(minIncome), d(maxIncome), percent, open))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining(setting);
    }

    @Test
    void nullSettingsStopTheApplicationToo() {
        assertThatThrownBy(() -> new InstallmentPlanner(pricing, applications, users, null, d("1"), d("1"), d("1"), 60, 2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InstallmentPlanner(pricing, applications, users, d("1"), null, d("1"), d("1"), 60, 2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InstallmentPlanner(pricing, applications, users, d("1"), d("1"), null, d("1"), 60, 2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new InstallmentPlanner(pricing, applications, users, d("1"), d("1"), d("1"), null, 60, 2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void boundarySettingsAreAccepted() {
        new InstallmentPlanner(pricing, applications, users, d("1"), d("1"), d("1"), d("1"), 1, 1);
        new InstallmentPlanner(pricing, applications, users, d("5"), d("5"), d("3"), d("3"), 100, 10);
    }

    // ------------------------------------------------------------------ terms and config

    @Test
    void termsAreTheOnesPricingOffersInTheOrderOfTheOffer() {
        List<InstallmentTermResponse> terms = planner.terms();
        assertThat(terms).extracting(InstallmentTermResponse::termMonths).containsExactly(6, 9, 12);
        assertThat(terms).extracting(InstallmentTermResponse::annualRatePercent).containsExactly(d("12"), d("14"), d("16"));
        verify(pricing).offeredInstallmentRates(InstallmentRules_TERMS());
    }

    private static List<Integer> InstallmentRules_TERMS() {
        return com.ebike.service.installment.InstallmentRules.TERMS;
    }

    @Test
    void aTermSwitchedOffByTheAdminDisappearsFromTheOffer() {
        Map<Integer, BigDecimal> offered = new LinkedHashMap<>();
        offered.put(6, d("12"));
        offered.put(12, d("16"));
        when(pricing.offeredInstallmentRates(any())).thenReturn(offered);
        assertThat(planner.terms()).extracting(InstallmentTermResponse::termMonths).containsExactly(6, 12);
    }

    @Test
    void configExposesTheLimitsAndTheNoteLengths() {
        InstallmentConfigResponse c = planner.config();
        assertThat(c.minDownPaymentPercent()).isEqualTo(20);
        assertThat(c.maxDownPaymentPercent()).isEqualTo(50);
        assertThat(c.terms()).hasSize(3);
        assertThat(c.minMonthlyIncome()).isEqualByComparingTo("3000000");
        assertThat(c.maxMonthlyIncome()).isEqualByComparingTo("1000000000");
        assertThat(c.maxPaymentPercentOfIncome()).isEqualTo(60);
        assertThat(c.maxOpenApplications()).isEqualTo(2);
        assertThat(c.maxApprovalNoteLength()).isEqualTo(1000);
        assertThat(c.maxRejectionNoteLength()).isEqualTo(1000 - InstallmentService.REJECTED_REASON_PREFIX.length()).isLessThan(1000);
    }

    // ------------------------------------------------------------------ quote

    @Test
    void quoteOfAnEligibleAmount() {
        InstallmentQuoteResponse q = planner.quote(d("20000000"), 20, 6);
        assertThat(q.amount()).isEqualByComparingTo("20000000");
        assertThat(q.downPaymentPercent()).isEqualTo(20);
        assertThat(q.downPayment()).isEqualByComparingTo("4000000");
        assertThat(q.financed()).isEqualByComparingTo("16000000");
        assertThat(q.termMonths()).isEqualTo(6);
        assertThat(q.annualRatePercent()).isEqualByComparingTo("12");
        assertThat(q.totalInterest()).isEqualByComparingTo("960000");
        assertThat(q.monthlyPayment()).isEqualByComparingTo("2826667");
        assertThat(q.totalPayable()).isEqualByComparingTo("20960002");
        assertThat(q.eligible()).isTrue();
        assertThat(q.reason()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"5000000,true", "4999999,false", "300000000,true", "300000001,false", "1000000,false", "999999999999,false"})
    void quoteEligibilityFollowsTheTotalLimitsAndStillShowsTheNumbers(String amount, boolean eligible) {
        InstallmentQuoteResponse q = planner.quote(d(amount), 20, 12);
        assertThat(q.eligible()).isEqualTo(eligible);
        assertThat(q.monthlyPayment()).isNotNull();
        if (eligible) {
            assertThat(q.reason()).isNull();
        } else {
            assertThat(q.reason()).contains("5.000.000").contains("300.000.000");
        }
    }

    @Test
    void quoteUsesTheRateOfTheChosenTerm() {
        assertThat(planner.quote(d("20000000"), 20, 9).annualRatePercent()).isEqualByComparingTo("14");
        assertThat(planner.quote(d("20000000"), 20, 12).annualRatePercent()).isEqualByComparingTo("16");
    }

    @Test
    void quoteRefusesANullZeroOrNegativeAmount() {
        assertThat(refused(() -> planner.quote(null, 20, 6)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> planner.quote(BigDecimal.ZERO, 20, 6)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> planner.quote(d("-100"), 20, 6)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void quoteRefusesAHugeAmountOrTooManyDecimalsBeforeComputing() {
        assertThat(refused(() -> planner.quote(d("1e20000000"), 20, 6)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> planner.quote(d("10000000.12345"), 20, 6)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> planner.quote(d("1000000000001"), 20, 6)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aTinyAmountDoesNotCrashAndIsNotEligible() {
        InstallmentQuoteResponse q = planner.quote(d("0.0001"), 20, 6);
        assertThat(q.eligible()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 19, 51, 100})
    void quoteRefusesADownPaymentOutsideTwentyToFifty(int percent) {
        ResponseStatusException e = refused(() -> planner.quote(d("20000000"), percent, 6));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("20%").contains("50%");
    }

    @Test
    void quoteRefusesAMissingDownPaymentOrTerm() {
        assertThat(refused(() -> planner.quote(d("20000000"), null, 6)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> planner.quote(d("20000000"), 20, null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 7, 18, 24, -6})
    void quoteRefusesATermThatIsNotOnTheMenu(int term) {
        assertThat(refused(() -> planner.quote(d("20000000"), 20, term)).getReason()).contains("Kỳ hạn");
    }

    @Test
    void quoteRefusesATermTheAdminSwitchedOff() {
        when(pricing.installmentTermOffered(9)).thenReturn(false);
        assertThat(refused(() -> planner.quote(d("20000000"), 20, 9)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(planner.quote(d("20000000"), 20, 6)).isNotNull();
    }

    // ------------------------------------------------------------------ prepare

    static final String ID = "079123456789";

    @Test
    void anAnonymousVisitorCannotApply() {
        ResponseStatusException e = refused(() -> planner.prepare(null, d("20000000"), request(ID, "10000000", 20, 6)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aMissingFormIsABadRequest() {
        assertThat(refused(() -> planner.prepare(user, d("20000000"), null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aValidApplicationIsPricedAndTheIdNumberMasked() {
        Prepared p = planner.prepare(user, d("20000000"), request("079 123 456 789", "10000000", 20, 6));
        assertThat(p.idNumberMasked()).isEqualTo("********6789");
        assertThat(p.monthlyIncome()).isEqualByComparingTo("10000000");
        assertThat(p.plan().monthlyPayment()).isEqualByComparingTo("2826667");
        assertThat(p.plan().downPayment()).isEqualByComparingTo("4000000");
    }

    @Test
    void theCustomersRowIsLockedBeforeTheOpenCountIsRead() {
        planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(users, applications);
        order.verify(users).findLockedById(7L);
        order.verify(applications).countOpenOf(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4999999", "300000001", "0", "-1"})
    void anOrderOutsideTheFinancedRangeIsRefused(String total) {
        ResponseStatusException e = refused(() -> planner.prepare(user, d(total), request(ID, "10000000", 20, 6)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("Trả góp chỉ áp dụng");
    }

    @Test
    void exactlyTheLimitsOfTheOrderTotalAreFinanced() {
        planner.prepare(user, d("5000000"), request(ID, "10000000", 20, 12));
        planner.prepare(user, d("300000000"), request(ID, "1000000000", 50, 12));
    }

    @Test
    void aBadDownPaymentOrTermIsRefusedBeforeAnythingElse() {
        assertThat(refused(() -> planner.prepare(user, d("20000000"), request(ID, "10000000", 10, 6))).getReason()).contains("trả trước");
        assertThat(refused(() -> planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 5))).getReason()).contains("Kỳ hạn");
        assertThat(refused(() -> planner.prepare(user, d("20000000"), request(ID, "10000000", null, 6))).getReason()).contains("trả trước");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "12345678", "1234567890123", "abcdefghijkl", "000123456789", "111111111111", "097123456789"})
    void aBadIdNumberIsRefused(String id) {
        ResponseStatusException e = refused(() -> planner.prepare(user, d("20000000"), request(id, "10000000", 20, 6)));
        assertThat(e.getReason()).contains("CCCD");
    }

    @Test
    void aNullIdNumberIsRefused() {
        assertThat(refused(() -> planner.prepare(user, d("20000000"), request(null, "10000000", 20, 6))).getReason()).contains("CCCD");
    }

    @Test
    void incomeWithCentsIsRefusedButTrailingZerosAreFine() {
        assertThat(refused(() -> planner.prepare(user, d("20000000"), request(ID, "10000000.50", 20, 6))).getReason()).contains("số đồng nguyên");
        assertThat(refused(() -> planner.prepare(user, d("20000000"), request(ID, "10000000.001", 20, 6))).getReason()).contains("số đồng nguyên");
        planner.prepare(user, d("20000000"), request(ID, "10000000.00", 20, 6));
        planner.prepare(user, d("20000000"), request(ID, "1E+7", 20, 6));
    }

    @Test
    void missingIncomeIsRefusedWithTheRangeMessage() {
        ResponseStatusException e = refused(() -> planner.prepare(user, d("20000000"), request(ID, null, 20, 6)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("Thu nhập").contains("3.000.000").contains("1.000.000.000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2999999", "1000000001", "0", "-3000000"})
    void incomeOutsideTheRangeIsRefused(String income) {
        assertThat(refused(() -> planner.prepare(user, d("20000000"), request(ID, income, 20, 6))).getReason()).contains("Thu nhập hằng tháng phải từ");
    }

    @Test
    void incomeOnTheLimitsIsAccepted() {
        planner.prepare(user, d("5000000"), request(ID, "3000000", 50, 12));
        planner.prepare(user, d("20000000"), request(ID, "1000000000", 20, 6));
    }

    @Test
    void aMonthlyPaymentAboveSixtyPercentOfIncomeIsRefused() {
        // monthly 2,826,667 against 4,711,111 x 60% = 2,826,666.6
        ResponseStatusException e = refused(() -> planner.prepare(user, d("20000000"), request(ID, "4711111", 20, 6)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("60%").contains("2.826.667");
        verify(applications, never()).countOpenOf(anyLong());
    }

    @Test
    void aMonthlyPaymentExactlySixtyPercentOfIncomeIsAccepted() {
        // 20,000,000 at 20% / 6 months / 12% -> 2,826,667; income 4,711,112 x 0.6 = 2,826,667.2 >= payment; income 4,711,111 x 0.6 = 2,826,666.6 < payment
        planner.prepare(user, d("20000000"), request(ID, "4711112", 20, 6));
    }

    @Test
    void aLongerTermMakesAnOtherwiseUnaffordablePlanAffordable() {
        assertThat(refused(() -> planner.prepare(user, d("20000000"), request(ID, "4000000", 20, 6))).getReason()).contains("Hãy tăng khoản trả trước");
        planner.prepare(user, d("20000000"), request(ID, "4000000", 20, 12));
    }

    @Test
    void twoOpenApplicationsAlreadyBlockANewOne() {
        when(applications.countOpenOf(7L)).thenReturn(2L);
        ResponseStatusException e = refused(() -> planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getReason()).contains("tối đa 2 hồ sơ");
    }

    @Test
    void oneOpenApplicationStillAllowsASecond() {
        when(applications.countOpenOf(7L)).thenReturn(1L);
        planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6));
    }

    @Test
    void theOpenLimitFollowsTheSetting() {
        InstallmentPlanner three = new InstallmentPlanner(pricing, applications, users, d("5000000"), d("300000000"), d("3000000"), d("1000000000"), 60, 3);
        when(applications.countOpenOf(7L)).thenReturn(2L);
        three.prepare(user, d("20000000"), request(ID, "10000000", 20, 6));
        when(applications.countOpenOf(7L)).thenReturn(3L);
        assertThat(refused(() -> three.prepare(user, d("20000000"), request(ID, "10000000", 20, 6))).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void otherOpenPlansCountAgainstTheSixtyPercentOfIncome() {
        // this plan 2,826,667; income 10,000,000 -> 6,000,000 allowed; others 3,173,333 -> sum 6,000,000 exactly: accepted
        when(applications.sumMonthlyPaymentOf(anyLong(), any())).thenReturn(d("3173333"));
        planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6));
        // one dong more and it is refused
        when(applications.sumMonthlyPaymentOf(anyLong(), any())).thenReturn(d("3173334"));
        ResponseStatusException e = refused(() -> planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("Cùng với các hồ sơ trả góp khác").contains("3.173.334").contains("60%");
    }

    @Test
    void noOtherPlansMeansNoExtraBurden() {
        when(applications.sumMonthlyPaymentOf(anyLong(), any())).thenReturn(null);
        planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6));
    }

    @Test
    void otherPlansOlderThanTheLongestTermAreNotCounted() {
        LocalDateTime before = LocalDateTime.now();
        planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6));
        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(applications).sumMonthlyPaymentOf(org.mockito.ArgumentMatchers.eq(7L), since.capture());
        assertThat(since.getValue()).isBetween(before.minusMonths(12), after.minusMonths(12));
    }

    @Test
    void theApplicationIsPricedWithTheRateOfThatMomentNotARateTheCustomerSent() {
        // the request carries no rate at all: the server recomputes everything
        when(pricing.installmentRate(6)).thenReturn(d("20"));
        Prepared p = planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6));
        assertThat(p.plan().annualRatePercent()).isEqualByComparingTo("20");
        assertThat(p.plan().totalInterest()).isEqualByComparingTo("1600000");
    }

    // ------------------------------------------------------------------ record

    @Test
    void recordSavesThePlanExactlyAsShownToTheCustomer() {
        Prepared p = planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6));
        Order order = new Order();
        order.setId(99L);
        InstallmentApplication saved = planner.record(order, user, p);

        assertThat(saved.getOrder()).isSameAs(order);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getStatus()).isEqualTo(InstallmentApplication.PENDING);
        assertThat(saved.getIdNumberMasked()).isEqualTo("********6789");
        assertThat(saved.getMonthlyIncome()).isEqualByComparingTo("10000000");
        assertThat(saved.getOrderTotal()).isEqualByComparingTo("20000000");
        assertThat(saved.getDownPaymentPercent()).isEqualTo(20);
        assertThat(saved.getDownPaymentAmount()).isEqualByComparingTo("4000000");
        assertThat(saved.getFinancedAmount()).isEqualByComparingTo("16000000");
        assertThat(saved.getTermMonths()).isEqualTo(6);
        assertThat(saved.getAnnualRatePercent()).isEqualByComparingTo("12");
        assertThat(saved.getMonthlyPayment()).isEqualByComparingTo("2826667");
        assertThat(saved.getTotalPayable()).isEqualByComparingTo("20960002");
    }

    @Test
    void theRawIdNumberIsNeverHandedToTheRepository() {
        Prepared p = planner.prepare(user, d("20000000"), request(ID, "10000000", 20, 6));
        planner.record(new Order(), user, p);
        ArgumentCaptor<InstallmentApplication> saved = ArgumentCaptor.forClass(InstallmentApplication.class);
        verify(applications).save(saved.capture());
        assertThat(saved.getValue().getIdNumberMasked()).doesNotContain("0791").doesNotContain("12345").endsWith("6789");
    }
}
