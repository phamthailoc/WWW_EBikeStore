package com.ebike.event.notification;

public record OrderCreatedEvent(
    Long userId,
    Long orderId,
    Long paymentId,
    String orderNumber,
    String status
) {
}
