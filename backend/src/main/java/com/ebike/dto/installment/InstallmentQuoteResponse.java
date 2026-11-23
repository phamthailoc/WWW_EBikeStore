package com.ebike.dto.installment;

import java.math.BigDecimal;

/**
 * V24: the plan for an amount. {@code eligible} is false when the amount is outside what the store finances ({@code reason} says why, in the language of
 * the request); the numbers are still shown so a visitor can see what the plan would be.
 */
public record InstallmentQuoteResponse(
    BigDecimal amount,
    int downPaymentPercent,
    BigDecimal downPayment,
    BigDecimal financed,
    int termMonths,
    BigDecimal annualRatePercent,
    BigDecimal totalInterest,
    BigDecimal monthlyPayment,
    BigDecimal totalPayable,
    boolean eligible,
    String reason
) {
}
