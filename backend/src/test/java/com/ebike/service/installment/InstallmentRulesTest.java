package com.ebike.service.installment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** V24: the arithmetic and the checks of a simulated instalment plan. */
class InstallmentRulesTest {

    private static final InstallmentRules.Limits LIMITS = new InstallmentRules.Limits(
        new BigDecimal("5000000"), new BigDecimal("300000000"), new BigDecimal("3000000"), new BigDecimal("1000000000"),
        new BigDecimal("0.60"), 2);

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aTwelveMonthPlanSplitsTheFinancedAmountPlusFlatInterest() {
        // 30% down on 30,000,000 = 9,000,000; financed 21,000,000; interest 10% a year for 12 months = 2,100,000; (21,000,000 + 2,100,000) / 12 = 1,925,000
        InstallmentRules.Plan plan = InstallmentRules.plan(money("30000000"), 30, 12, money("10"));
        assertEquals(0, plan.downPayment().compareTo(money("9000000")));
        assertEquals(0, plan.financed().compareTo(money("21000000")));
        assertEquals(0, plan.totalInterest().compareTo(money("2100000")));
        assertEquals(0, plan.monthlyPayment().compareTo(money("1925000")));
        assertEquals(0, plan.totalPayable().compareTo(money("32100000")));
    }

    @Test
    void theInterestFollowsTheTermNotAWholeYear() {
        // 6 months at 8% a year on 10,000,000 financed: 10,000,000 x 8% x 6/12 = 400,000
        InstallmentRules.Plan plan = InstallmentRules.plan(money("12500000"), 20, 6, money("8"));
        assertEquals(0, plan.financed().compareTo(money("10000000")));
        assertEquals(0, plan.totalInterest().compareTo(money("400000")));
        assertEquals(0, plan.monthlyPayment().compareTo(money("1733334"))); // 10,400,000 / 6 = 1,733,333.33 rounds up
    }

    @Test
    void theMonthlyPaymentRoundsUpSoTheInstalmentsNeverFallShort() {
        InstallmentRules.Plan plan = InstallmentRules.plan(money("20300000"), 25, 9, money("9"));
        BigDecimal paidInInstalments = plan.monthlyPayment().multiply(BigDecimal.valueOf(9));
        assertTrue(paidInInstalments.compareTo(plan.financed().add(plan.totalInterest())) >= 0);
        // and by less than one dong a month
        assertTrue(paidInInstalments.subtract(plan.financed().add(plan.totalInterest())).compareTo(BigDecimal.valueOf(9)) < 0);
        assertEquals(0, plan.totalPayable().compareTo(plan.downPayment().add(paidInInstalments)));
    }

    @Test
    void aZeroRateMeansNoInterest() {
        InstallmentRules.Plan plan = InstallmentRules.plan(money("12000000"), 50, 6, BigDecimal.ZERO);
        assertEquals(0, plan.totalInterest().compareTo(BigDecimal.ZERO));
        assertEquals(0, plan.monthlyPayment().compareTo(money("1000000")));
        assertEquals(0, plan.totalPayable().compareTo(money("12000000")));
    }

    @Test
    void aTotalWithFractionsIsRoundedToWholeDong() {
        InstallmentRules.Plan plan = InstallmentRules.plan(money("10000000.60"), 20, 6, money("8"));
        assertEquals(0, plan.orderTotal().compareTo(money("10000001")));
        assertEquals(0, plan.downPayment().compareTo(money("2000000"))); // 20% of 10,000,001 = 2,000,000.2
    }

    @Test
    void theDownPaymentRangeIsTwentyToFiftyPercentInclusive() {
        assertFalse(InstallmentRules.isValidDownPercent(19));
        assertTrue(InstallmentRules.isValidDownPercent(20));
        assertTrue(InstallmentRules.isValidDownPercent(50));
        assertFalse(InstallmentRules.isValidDownPercent(51));
        assertFalse(InstallmentRules.isValidDownPercent(0));
        assertFalse(InstallmentRules.isValidDownPercent(-30));
    }

    @Test
    void onlySixNineAndTwelveMonthTermsAreOffered() {
        assertTrue(InstallmentRules.isOfferedTerm(6));
        assertTrue(InstallmentRules.isOfferedTerm(9));
        assertTrue(InstallmentRules.isOfferedTerm(12));
        assertFalse(InstallmentRules.isOfferedTerm(3));
        assertFalse(InstallmentRules.isOfferedTerm(24));
        assertFalse(InstallmentRules.isOfferedTerm(0));
    }

    @Test
    void anOrderMustBeInsideTheFinancingRange() {
        assertFalse(InstallmentRules.totalInRange(money("4999999"), LIMITS));
        assertTrue(InstallmentRules.totalInRange(money("5000000"), LIMITS));
        assertTrue(InstallmentRules.totalInRange(money("300000000"), LIMITS));
        assertFalse(InstallmentRules.totalInRange(money("300000001"), LIMITS));
    }

    @Test
    void theIncomeMustBeInsideItsRange() {
        assertFalse(InstallmentRules.incomeInRange(null, LIMITS));
        assertFalse(InstallmentRules.incomeInRange(money("2999999"), LIMITS));
        assertTrue(InstallmentRules.incomeInRange(money("3000000"), LIMITS));
        assertTrue(InstallmentRules.incomeInRange(money("1000000000"), LIMITS));
        assertFalse(InstallmentRules.incomeInRange(money("1000000001"), LIMITS));
    }

    @Test
    void theMonthlyPaymentMayTakeAtMostTheLimitShareOfIncome() {
        // 60% of 10,000,000 is 6,000,000: the boundary itself is allowed
        assertTrue(InstallmentRules.affordable(money("6000000"), money("10000000"), LIMITS));
        assertFalse(InstallmentRules.affordable(money("6000001"), money("10000000"), LIMITS));
        assertTrue(InstallmentRules.affordable(money("1"), money("3000000"), LIMITS));
    }

    @Test
    void anIdNumberIsNineOrTwelveDigitsWhateverTheSeparators() {
        assertEquals("079123456789", InstallmentRules.normalizeIdNumber("079123456789"));
        assertEquals("079123456789", InstallmentRules.normalizeIdNumber(" 079 123 456 789 "));
        assertEquals("079123456789", InstallmentRules.normalizeIdNumber("079.123.456-789"));
        assertEquals("123456789", InstallmentRules.normalizeIdNumber("123456789"));
        assertNull(InstallmentRules.normalizeIdNumber(null));
        assertNull(InstallmentRules.normalizeIdNumber(""));
        assertNull(InstallmentRules.normalizeIdNumber("12345678"));
        assertNull(InstallmentRules.normalizeIdNumber("1234567890"));
        assertNull(InstallmentRules.normalizeIdNumber("07912345678a"));
        assertNull(InstallmentRules.normalizeIdNumber("0791234567890"));
    }

    @Test
    void onlyTheLastFourDigitsOfAnIdNumberSurviveMasking() {
        assertEquals("********6789", InstallmentRules.maskIdNumber("079123456789"));
        assertEquals("*****6789", InstallmentRules.maskIdNumber("123456789"));
        assertEquals("6789", InstallmentRules.maskIdNumber("6789"));
    }
}
