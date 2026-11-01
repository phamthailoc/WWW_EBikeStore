package com.ebike.event.notification;

public record OrderStatusChangedEvent(
    Long userId,
    Long orderId,
    Long paymentId,
    String orderNumber,
    String status
) {
}
