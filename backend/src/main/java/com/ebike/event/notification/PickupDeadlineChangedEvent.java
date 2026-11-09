package com.ebike.event.notification;

import java.time.LocalDateTime;

public record PickupDeadlineChangedEvent(
    Long userId,
    Long orderId,
    Long paymentId,
    String orderNumber,
    LocalDateTime pickupDeadlineAt
) {
}
