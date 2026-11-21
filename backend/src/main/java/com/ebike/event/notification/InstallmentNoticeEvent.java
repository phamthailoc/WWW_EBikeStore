package com.ebike.event.notification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V24: the staff decided an instalment application and the customer should be told: {@code kind} is one of the constants of
 * InstallmentMessages (APPROVED, REJECTED). {@code payBefore} is when the down payment is due (approvals only); {@code note} is the staff's words.
 */
public record InstallmentNoticeEvent(
    Long userId,
    Long orderId,
    String orderNumber,
    String kind,
    BigDecimal downPayment,
    BigDecimal monthlyPayment,
    int termMonths,
    LocalDateTime payBefore,
    String note
) {
}
