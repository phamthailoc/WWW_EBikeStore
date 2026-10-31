package com.ebike.orderModule.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VnPayCreatePaymentResponse(
    String paymentUrl,
    Long paymentId,
    String txnRef,
    BigDecimal amount,
    LocalDateTime expireTime,
    String orderNumber
) {
}
