package com.ebike.event.notification;

public record PaymentStatusChangedEvent(
    Long userId,
    Long orderId,
    Long paymentId,
    String orderNumber,
    String paymentStatus
) {
}
