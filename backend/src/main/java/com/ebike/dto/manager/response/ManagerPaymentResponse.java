package com.ebike.dto.manager.response;

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
    LocalDateTime orderCreatedAt,
    String orderStatus,
    /** V24: the status of the instalment application when this is the down payment of an instalment order, else null. */
    String installmentStatus,
    /** V24: true when this is an instalment down payment past its hold: the expiry job is about to cancel the order, so it can no longer be taken. */
    boolean downPaymentOverdue
) {
}
