package com.ebike.dto.installment;

import java.math.BigDecimal;
import java.util.List;

/** V24: what the calculator and the checkout form need to know: the terms on offer (an inactive one is left out) and the limits, including how long the staff's note can be. */
public record InstallmentConfigResponse(
    int minDownPaymentPercent,
    int maxDownPaymentPercent,
    List<InstallmentTermResponse> terms,
    BigDecimal minMonthlyIncome,
    int maxPaymentPercentOfIncome,
    int maxOpenApplications,
    int maxApprovalNoteLength,
    int maxRejectionNoteLength
) {
}
