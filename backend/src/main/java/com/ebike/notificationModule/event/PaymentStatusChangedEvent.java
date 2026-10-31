package com.ebike.notificationModule.event;

public record PaymentStatusChangedEvent(
    Long userId,
    Long orderId,
    Long paymentId,
    String orderNumber,
    String paymentStatus
) {
}
