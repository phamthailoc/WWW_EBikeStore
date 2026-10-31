package com.ebike.notificationModule.event;

public record OrderStatusChangedEvent(
    Long userId,
    Long orderId,
    Long paymentId,
    String orderNumber,
    String status
) {
}
