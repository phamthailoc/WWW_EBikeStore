package com.ebike.dto.order.response;

import java.math.BigDecimal;

public record VnPayReturnResponse(
    boolean valid,
    boolean success,
    String responseCode,
    String txnRef,
    BigDecimal amount,
    String transactionNo,
    String bankCode,
    String cardType,
    Long paymentId,
    Long orderId,
    String orderNumber,
    String paymentStatus
) {
}
