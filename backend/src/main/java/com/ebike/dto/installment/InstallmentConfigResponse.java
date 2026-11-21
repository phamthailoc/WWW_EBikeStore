package com.ebike.dto.installment;

import java.math.BigDecimal;
import java.util.List;

/** V24: what the calculator and the checkout form need to know: the terms on offer (an inactive one is left out) and the limits. */
public record InstallmentConfigResponse(
    int minDownPaymentPercent,
    int maxDownPaymentPercent,
    List<InstallmentTermResponse> terms,
    BigDecimal minOrderTotal,
    BigDecimal maxOrderTotal,
    BigDecimal minMonthlyIncome,
    int maxPaymentPercentOfIncome,
    int maxOpenApplications
) {
}
