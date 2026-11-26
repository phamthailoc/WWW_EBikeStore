package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.service.installment.InstallmentRules;
import com.ebike.service.installment.InstallmentRules.Limits;
import com.ebike.service.installment.InstallmentRules.Plan;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InstallmentRulesTest {

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static final Limits LIMITS = new Limits(d("5000000"), d("300000000"), d("3000000"), d("1000000000"), d("0.60"), 2);

    // ------------------------------------------------------------------ constants of the offer

    @Test
    void termsAre6_9_12InThatOrder() {
        assertThat(InstallmentRules.TERMS).containsExactly(6, 9, 12);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 9, 12})
    void offeredTerms(int months) {
        assertThat(InstallmentRules.isOfferedTerm(months)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {-6, 0, 1, 3, 7, 8, 10, 11, 13, 18, 24, 36, 60})
    void termsOutsideTheOfferAreRefused(int months) {
        assertThat(InstallmentRules.isOfferedTerm(months)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 21, 25, 49, 50})
    void downPaymentInsideTwentyToFiftyPercent(int percent) {
        assertThat(InstallmentRules.isValidDownPercent(percent)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -20, 0, 1, 19, 51, 100, Integer.MAX_VALUE})
    void downPaymentOutsideTwentyToFiftyPercent(int percent) {
        assertThat(InstallmentRules.isValidDownPercent(percent)).isFalse();
    }

    // ------------------------------------------------------------------ the arithmetic

    @Test
    void sixMonthPlanOfTenMillionAt20PercentDownAnd12PercentAYear() {
        Plan p = InstallmentRules.plan(d("10000000"), 20, 6, d("12"));
        assertThat(p.orderTotal()).isEqualByComparingTo("10000000");
        assertThat(p.downPayment()).isEqualByComparingTo("2000000");
        assertThat(p.financed()).isEqualByComparingTo("8000000");
        assertThat(p.totalInterest()).isEqualByComparingTo("480000"); // 8,000,000 x 12% x 6/12
        assertThat(p.monthlyPayment()).isEqualByComparingTo("1413334"); // 8,480,000 / 6 = 1,413,333.33 rounded UP
        assertThat(p.totalPayable()).isEqualByComparingTo("10480004"); // 2,000,000 + 6 x 1,413,334
        assertThat(p.downPaymentPercent()).isEqualTo(20);
        assertThat(p.termMonths()).isEqualTo(6);
        assertThat(p.annualRatePercent()).isEqualByComparingTo("12");
    }

    @Test
    void biggestPlanThreeHundredMillionHalfDownTwelveMonthsAtSixtyPercent() {
        Plan p = InstallmentRules.plan(d("300000000"), 50, 12, d("60"));
        assertThat(p.downPayment()).isEqualByComparingTo("150000000");
        assertThat(p.financed()).isEqualByComparingTo("150000000");
        assertThat(p.totalInterest()).isEqualByComparingTo("90000000");
        assertThat(p.monthlyPayment()).isEqualByComparingTo("20000000");
        assertThat(p.totalPayable()).isEqualByComparingTo("390000000");
    }

    @Test
    void nineMonthPlanWithAFractionalRate() {
        Plan p = InstallmentRules.plan(d("5000000"), 20, 9, d("8.5"));
        assertThat(p.downPayment()).isEqualByComparingTo("1000000");
        assertThat(p.financed()).isEqualByComparingTo("4000000");
        assertThat(p.totalInterest()).isEqualByComparingTo("255000");
        assertThat(p.monthlyPayment()).isEqualByComparingTo("472778"); // 4,255,000 / 9 = 472,777.78 rounded up
        assertThat(p.totalPayable()).isEqualByComparingTo("5255002");
    }

    @Test
    void zeroRateMeansNoInterest() {
        Plan p = InstallmentRules.plan(d("12000000"), 25, 12, BigDecimal.ZERO);
        assertThat(p.totalInterest()).isEqualByComparingTo("0");
        assertThat(p.monthlyPayment()).isEqualByComparingTo("750000"); // 9,000,000 / 12
        assertThat(p.totalPayable()).isEqualByComparingTo("12000000");
    }

    @Test
    void downPaymentRoundsHalfUp() {
        // 5,000,010 x 25% = 1,250,002.5 -> 1,250,003
        Plan p = InstallmentRules.plan(d("5000010"), 25, 6, BigDecimal.ZERO);
        assertThat(p.downPayment()).isEqualByComparingTo("1250003");
        assertThat(p.financed()).isEqualByComparingTo("3750007");
        assertThat(p.downPayment().add(p.financed())).isEqualByComparingTo(p.orderTotal());
    }

    @Test
    void interestRoundsHalfUp() {
        // financed 800 (total 1000, 20% down), rate 7, 9 months: 800 x 7 x 9 / 1200 = 42 exactly; use 1000 financed -> 52.5
        Plan p = InstallmentRules.plan(d("1250"), 20, 9, d("7")); // down 250, financed 1000
        assertThat(p.financed()).isEqualByComparingTo("1000");
        assertThat(p.totalInterest()).isEqualByComparingTo("53"); // 52.5 -> 53
    }

    @Test
    void orderTotalIsRoundedToWholeDongHalfUpFirst() {
        assertThat(InstallmentRules.plan(d("5000000.5"), 20, 6, BigDecimal.ZERO).orderTotal()).isEqualByComparingTo("5000001");
        assertThat(InstallmentRules.plan(d("5000000.49"), 20, 6, BigDecimal.ZERO).orderTotal()).isEqualByComparingTo("5000000");
    }

    @Test
    void everyMoneyFigureOfAPlanIsAWholeNumberOfDong() {
        Plan p = InstallmentRules.plan(d("17333333.33"), 33, 9, d("13.37"));
        for (BigDecimal amount : new BigDecimal[] {p.orderTotal(), p.downPayment(), p.financed(), p.totalInterest(), p.monthlyPayment(), p.totalPayable()}) {
            assertThat(amount.scale()).as(amount.toPlainString()).isLessThanOrEqualTo(0);
        }
    }

    @Test
    void theInstalmentsNeverAddUpToLessThanTheFinancedAmountPlusInterestAndOvershootByLessThanOneDongPerMonth() {
        Random random = new Random(20261123);
        int[] terms = {6, 9, 12};
        for (int i = 0; i < 5000; i++) {
            BigDecimal total = BigDecimal.valueOf(5_000_000L + (long) (random.nextDouble() * 295_000_000L));
            int percent = 20 + random.nextInt(31);
            int term = terms[random.nextInt(3)];
            BigDecimal rate = BigDecimal.valueOf(random.nextInt(6001), 2); // 0.00 .. 60.00
            Plan p = InstallmentRules.plan(total, percent, term, rate);

            assertThat(p.downPayment().add(p.financed())).as("down + financed = total").isEqualByComparingTo(p.orderTotal());
            BigDecimal instalments = p.monthlyPayment().multiply(BigDecimal.valueOf(term));
            BigDecimal due = p.financed().add(p.totalInterest());
            assertThat(instalments).as("never less than financed + interest: " + p).isGreaterThanOrEqualTo(due);
            assertThat(instalments.subtract(due)).as("overshoot < term dong: " + p).isLessThan(BigDecimal.valueOf(term));
            assertThat(instalments).isGreaterThanOrEqualTo(p.financed());
            assertThat(p.totalPayable()).isEqualByComparingTo(p.downPayment().add(instalments));
            assertThat(p.downPayment().signum()).isPositive();
            assertThat(p.monthlyPayment().signum()).isPositive();
        }
    }

    @Test
    void interestGrowsWithTheTermAndWithTheRate() {
        BigDecimal base = InstallmentRules.plan(d("50000000"), 20, 6, d("10")).totalInterest();
        assertThat(InstallmentRules.plan(d("50000000"), 20, 12, d("10")).totalInterest()).isGreaterThan(base);
        assertThat(InstallmentRules.plan(d("50000000"), 20, 6, d("11")).totalInterest()).isGreaterThan(base);
        assertThat(InstallmentRules.plan(d("50000000"), 30, 6, d("10")).totalInterest()).isLessThan(base);
    }

    @Test
    void longerTermsGiveASmallerMonthlyPaymentAtTheSameRate() {
        BigDecimal m6 = InstallmentRules.plan(d("50000000"), 20, 6, d("10")).monthlyPayment();
        BigDecimal m9 = InstallmentRules.plan(d("50000000"), 20, 9, d("10")).monthlyPayment();
        BigDecimal m12 = InstallmentRules.plan(d("50000000"), 20, 12, d("10")).monthlyPayment();
        assertThat(m6).isGreaterThan(m9);
        assertThat(m9).isGreaterThan(m12);
    }

    // ------------------------------------------------------------------ limits

    @ParameterizedTest
    @CsvSource({"5000000,true", "4999999,false", "4999999.99,false", "5000000.01,true", "300000000,true", "300000000.01,false", "300000001,false", "0,false", "-5000000,false"})
    void totalRangeIsInclusiveAtBothEnds(String total, boolean ok) {
        assertThat(InstallmentRules.totalInRange(d(total), LIMITS)).isEqualTo(ok);
    }

    @ParameterizedTest
    @CsvSource({"3000000,true", "2999999,false", "1000000000,true", "1000000001,false", "0,false", "-1,false", "50000000,true"})
    void incomeRangeIsInclusiveAtBothEnds(String income, boolean ok) {
        assertThat(InstallmentRules.incomeInRange(d(income), LIMITS)).isEqualTo(ok);
    }

    @Test
    void nullIncomeIsOutOfRange() {
        assertThat(InstallmentRules.incomeInRange(null, LIMITS)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"1800000,3000000,true", "1800001,3000000,false", "1,3000000,true", "0,3000000,true", "6000000,10000000,true", "6000001,10000000,false"})
    void paymentMayBeUpToSixtyPercentOfIncomeInclusive(String payment, String income, boolean ok) {
        assertThat(InstallmentRules.affordable(d(payment), d(income), LIMITS)).isEqualTo(ok);
    }

    @Test
    void shareOfIncomeFollowsTheSetting() {
        Limits half = new Limits(d("5000000"), d("300000000"), d("3000000"), d("1000000000"), d("0.50"), 2);
        assertThat(InstallmentRules.affordable(d("1500000"), d("3000000"), half)).isTrue();
        assertThat(InstallmentRules.affordable(d("1500001"), d("3000000"), half)).isFalse();
    }

    // ------------------------------------------------------------------ quotable amounts

    @Test
    @Timeout(5)
    void quotableAmountsAreBoundedInSizeAndScale() {
        assertThat(InstallmentRules.isQuotableAmount(d("1"))).isTrue();
        assertThat(InstallmentRules.isQuotableAmount(d("0.0001"))).isTrue();
        assertThat(InstallmentRules.isQuotableAmount(d("0.00001"))).isFalse(); // scale 5
        assertThat(InstallmentRules.isQuotableAmount(d("1000000000000"))).isTrue();
        assertThat(InstallmentRules.isQuotableAmount(d("1000000000001"))).isFalse();
        assertThat(InstallmentRules.isQuotableAmount(d("1E+3"))).isTrue();
        assertThat(InstallmentRules.isQuotableAmount(d("1e20000000"))).isFalse(); // would build a number of 20 million digits
        assertThat(InstallmentRules.isQuotableAmount(d("0"))).isFalse();
        assertThat(InstallmentRules.isQuotableAmount(d("-1"))).isFalse();
        assertThat(InstallmentRules.isQuotableAmount(d("0.000"))).isFalse();
        assertThat(InstallmentRules.isQuotableAmount(null)).isFalse();
    }

    // ------------------------------------------------------------------ down payment hold

    @Test
    void downPaymentIsOverdueOnlyAfterTheHoldHasPassed() {
        LocalDateTime reviewed = LocalDateTime.parse("2026-11-20T10:00");
        Duration hold = Duration.ofHours(72);
        assertThat(InstallmentRules.downPaymentOverdue(reviewed, hold, reviewed)).isFalse();
        assertThat(InstallmentRules.downPaymentOverdue(reviewed, hold, reviewed.plusHours(72))).isFalse(); // the deadline itself is still in time
        assertThat(InstallmentRules.downPaymentOverdue(reviewed, hold, reviewed.plusHours(72).plusNanos(1))).isTrue();
        assertThat(InstallmentRules.downPaymentOverdue(reviewed, hold, reviewed.plusHours(71).plusMinutes(59))).isFalse();
    }

    @Test
    void anApplicationNotReviewedYetIsNeverOverdue() {
        assertThat(InstallmentRules.downPaymentOverdue(null, Duration.ofHours(72), LocalDateTime.parse("2030-01-01T00:00"))).isFalse();
    }

    // ------------------------------------------------------------------ ID numbers

    @ParameterizedTest
    @ValueSource(strings = {"079123456789", "001123456789", "096123456789", "012345678901", "123456789", "000000001", "987654321"})
    void validIdNumbers(String id) {
        assertThat(InstallmentRules.normalizeIdNumber(id)).isEqualTo(id);
    }

    @Test
    void spacesDotsAndDashesAreStripped() {
        assertThat(InstallmentRules.normalizeIdNumber("079 123 456 789")).isEqualTo("079123456789");
        assertThat(InstallmentRules.normalizeIdNumber("079.123.456.789")).isEqualTo("079123456789");
        assertThat(InstallmentRules.normalizeIdNumber("079-123-456-789")).isEqualTo("079123456789");
        assertThat(InstallmentRules.normalizeIdNumber("  079123456789\t")).isEqualTo("079123456789");
        assertThat(InstallmentRules.normalizeIdNumber("123 456 789")).isEqualTo("123456789");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "12345678", "1234567890", "12345678901", "1234567890123", "07912345678a", "abcdefghijkl", "079/123/456/789",
        "000123456789", "097123456789", "099123456789", "100123456789",
        "000000000000", "111111111111", "999999999", "555555555555", "+84123456789", "٠٧٩١٢٣٤٥٦٧٨٩"})
    void invalidIdNumbers(String id) {
        assertThat(InstallmentRules.normalizeIdNumber(id)).isNull();
    }

    @Test
    void maskKeepsOnlyTheLastFourDigits() {
        assertThat(InstallmentRules.maskIdNumber("079123456789")).isEqualTo("********6789");
        assertThat(InstallmentRules.maskIdNumber("123456789")).isEqualTo("*****6789");
        assertThat(InstallmentRules.maskIdNumber("1234")).isEqualTo("1234");
        assertThat(InstallmentRules.maskIdNumber("123")).isEqualTo("123");
        assertThat(InstallmentRules.maskIdNumber("")).isEmpty();
    }

    @Test
    void theMaskNeverRevealsTheFirstEightDigits() {
        String masked = InstallmentRules.maskIdNumber("079123456789");
        assertThat(masked).doesNotContain("0791").doesNotContain("1234").hasSize(12);
    }

    @Test
    void quotableLimitConstantIsOneTrillion() {
        assertThat(InstallmentRules.MAX_QUOTE_AMOUNT).isEqualByComparingTo("1000000000000");
    }
}
