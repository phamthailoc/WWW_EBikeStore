package com.ebike.dto.order.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentHistoryResponse(
    Long id,
    Long orderId,
    String orderNumber,
    String transactionReference,
    BigDecimal amount,
    String currency,
    String paymentMethod,
    String paymentStatus,
    String providerTxnId,
    LocalDateTime paidAt,
    LocalDateTime createdAt
) {
}
