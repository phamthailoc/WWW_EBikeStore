package com.ebike.dto.installment;

import java.math.BigDecimal;

/** V24: what the customer fills in at checkout to pay by instalments. The ID number is masked before it is stored; the plan is recomputed by the server. */
public record InstallmentRequest(
    String idNumber,
    BigDecimal monthlyIncome,
    Integer downPaymentPercent,
    Integer termMonths
) {
}
