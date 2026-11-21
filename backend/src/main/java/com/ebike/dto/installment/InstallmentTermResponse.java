package com.ebike.dto.installment;

import java.math.BigDecimal;

/** V24: one term the store offers and its yearly flat rate (percent). */
public record InstallmentTermResponse(int termMonths, BigDecimal annualRatePercent) {
}
