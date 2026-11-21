package com.ebike.event.notification;

public record PaymentStatusChangedEvent(
    Long userId,
    Long orderId,
    Long paymentId,
    String orderNumber,
    String paymentStatus,
    // the payment method (PaymentMethod name): an instalment down payment is worded differently from a payment of a whole order
    String paymentMethod
) {
}
