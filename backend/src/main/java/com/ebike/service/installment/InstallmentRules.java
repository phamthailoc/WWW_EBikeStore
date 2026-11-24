package com.ebike.service.installment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * V24: the arithmetic and the checks of a simulated instalment plan, free of the database so they can be tested alone.
 *
 * <p>The interest is flat: the annual rate is applied to the amount left after the down payment, for the length of the term, and the
 * result is split over the months (so the monthly payment is the same every month). Money is whole dong; the down payment rounds half up
 * and the monthly payment rounds up, so the customer's instalments never add up to less than the amount financed.
 */
public final class InstallmentRules {

    /** The terms the store offers, in months. An admin can switch one off by setting its rate rule INACTIVE. */
    public static final List<Integer> TERMS = List.of(6, 9, 12);
    public static final int MIN_DOWN_PERCENT = 20;
    public static final int MAX_DOWN_PERCENT = 50;
    /** A Vietnamese citizen ID card has 12 digits; the old 9-digit identity card is still in use. */
    private static final int[] ID_LENGTHS = {9, 12};
    private static final int VISIBLE_DIGITS = 4;
    /** The largest amount a quote is worked out for: far above any order, and small enough that the arithmetic below stays instant. */
    public static final BigDecimal MAX_QUOTE_AMOUNT = new BigDecimal("1000000000000");
    private static final int MAX_QUOTE_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    /**
     * True once the down payment of an application approved at {@code reviewedAt} is past its hold: the expiry job cancels the order at its next run,
     * and until then nobody may still pay or collect the down payment (the money would arrive for an order that is about to be cancelled).
     */
    public static boolean downPaymentOverdue(LocalDateTime reviewedAt, Duration hold, LocalDateTime now) {
        return reviewedAt != null && now.isAfter(reviewedAt.plus(hold));
    }

    /** What an order may be financed for and by whom; the service reads the values from the settings. */
    public record Limits(BigDecimal minTotal, BigDecimal maxTotal, BigDecimal minIncome, BigDecimal maxIncome,
                         BigDecimal maxPaymentShareOfIncome, int maxOpenPerCustomer) {
    }

    /** A plan as the customer sees it. */
    public record Plan(BigDecimal orderTotal, int downPaymentPercent, BigDecimal downPayment, BigDecimal financed, int termMonths,
                       BigDecimal annualRatePercent, BigDecimal totalInterest, BigDecimal monthlyPayment, BigDecimal totalPayable) {
    }

    private InstallmentRules() {
    }

    public static boolean isOfferedTerm(int months) {
        return TERMS.contains(months);
    }

    public static boolean isValidDownPercent(int percent) {
        return percent >= MIN_DOWN_PERCENT && percent <= MAX_DOWN_PERCENT;
    }

    /**
     * Whether an amount typed into a public quote is safe to compute with. A number such as {@code 1e20000000} is a valid BigDecimal, but rounding it
     * or multiplying it would build a number of twenty million digits, so the scale and the size are checked first (both checks are cheap).
     */
    public static boolean isQuotableAmount(BigDecimal amount) {
        return amount != null && amount.scale() <= MAX_QUOTE_SCALE && amount.signum() > 0 && amount.compareTo(MAX_QUOTE_AMOUNT) <= 0;
    }

    /** The plan for an order total; the caller has checked the percent and the term. */
    public static Plan plan(BigDecimal orderTotal, int downPaymentPercent, int termMonths, BigDecimal annualRatePercent) {
        BigDecimal total = orderTotal.setScale(0, RoundingMode.HALF_UP);
        BigDecimal down = total.multiply(BigDecimal.valueOf(downPaymentPercent)).divide(HUNDRED, 0, RoundingMode.HALF_UP);
        BigDecimal financed = total.subtract(down);
        BigDecimal interest = financed.multiply(annualRatePercent).multiply(BigDecimal.valueOf(termMonths))
            .divide(HUNDRED.multiply(TWELVE), 0, RoundingMode.HALF_UP);
        BigDecimal monthly = financed.add(interest).divide(BigDecimal.valueOf(termMonths), 0, RoundingMode.CEILING);
        BigDecimal totalPayable = down.add(monthly.multiply(BigDecimal.valueOf(termMonths)));
        return new Plan(total, downPaymentPercent, down, financed, termMonths, annualRatePercent, interest, monthly, totalPayable);
    }

    /** Whether an order total may be financed at all. */
    public static boolean totalInRange(BigDecimal orderTotal, Limits limits) {
        return orderTotal.compareTo(limits.minTotal()) >= 0 && orderTotal.compareTo(limits.maxTotal()) <= 0;
    }

    public static boolean incomeInRange(BigDecimal income, Limits limits) {
        return income != null && income.compareTo(limits.minIncome()) >= 0 && income.compareTo(limits.maxIncome()) <= 0;
    }

    /** Whether the monthly payment leaves the customer enough of the declared income. */
    public static boolean affordable(BigDecimal monthlyPayment, BigDecimal monthlyIncome, Limits limits) {
        return monthlyPayment.compareTo(monthlyIncome.multiply(limits.maxPaymentShareOfIncome())) <= 0;
    }

    /** The digits of an ID number (spaces and dots allowed in the input), or null when it is not a 9 or 12 digit number. */
    public static String normalizeIdNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[\\s.\\-]", "");
        if (!digits.matches("\\d+")) {
            return null;
        }
        if (digits.chars().distinct().count() == 1) {
            return null; // 000000000000, 111111111111...: not a number anyone holds
        }
        for (int length : ID_LENGTHS) {
            if (digits.length() == length) {
                // a 12-digit citizen card starts with the code of a province (001-096)
                if (length == 12) {
                    int province = Integer.parseInt(digits.substring(0, 3));
                    if (province < 1 || province > 96) {
                        return null;
                    }
                }
                return digits;
            }
        }
        return null;
    }

    /** All digits but the last four become stars: the only form of the number the shop keeps. */
    public static String maskIdNumber(String digits) {
        int hidden = Math.max(0, digits.length() - VISIBLE_DIGITS);
        return "*".repeat(hidden) + digits.substring(hidden);
    }
}
