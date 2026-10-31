package com.ebike.notificationModule.event;

public record OrderCreatedEvent(
    Long userId,
    Long orderId,
    Long paymentId,
    String orderNumber,
    String status
) {
}
