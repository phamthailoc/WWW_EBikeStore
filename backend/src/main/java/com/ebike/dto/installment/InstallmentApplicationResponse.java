package com.ebike.dto.installment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V24: an instalment application. {@code idNumberMasked} and {@code monthlyIncome} are null once the account is erased; {@code reviewedBy} is only filled
 * for the staff's view. {@code payBefore} is when an approved application's down payment is due; {@code canPay} is true while the customer can pay it.
 */
public record InstallmentApplicationResponse(
    Long id,
    Long orderId,
    String orderNumber,
    String orderStatus,
    String customerName,
    String vehicles,
    String idNumberMasked,
    BigDecimal monthlyIncome,
    BigDecimal orderTotal,
    int downPaymentPercent,
    BigDecimal downPayment,
    BigDecimal financed,
    int termMonths,
    BigDecimal annualRatePercent,
    BigDecimal monthlyPayment,
    BigDecimal totalPayable,
    String status,
    String reviewNote,
    String reviewedBy,
    LocalDateTime reviewedAt,
    LocalDateTime createdAt,
    String paymentStatus,
    LocalDateTime payBefore,
    boolean canPay,
    boolean canDecide
) {
}
