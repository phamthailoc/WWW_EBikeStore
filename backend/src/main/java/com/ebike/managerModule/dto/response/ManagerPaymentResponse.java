package com.ebike.managerModule.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ManagerPaymentResponse(
    Long id,
    Long orderId,
    String orderNumber,
    String customerName,
    String customerEmail,
    BigDecimal amount,
    String currency,
    String paymentMethod,
    String paymentStatus,
    String transactionReference,
    String providerTxnId,
    String note,
    LocalDateTime paidAt,
    LocalDateTime orderCreatedAt
) {
}
