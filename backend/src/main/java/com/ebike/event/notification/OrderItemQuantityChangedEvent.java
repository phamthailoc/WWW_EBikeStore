package com.ebike.event.notification;

import java.math.BigDecimal;

/** Staff changed how many bikes of one line the order has: the customer is told the new quantity and total. */
public record OrderItemQuantityChangedEvent(
    Long userId,
    Long orderId,
    Long paymentId,
    String orderNumber,
    String productName,
    int quantityBefore,
    int quantityAfter,
    BigDecimal totalAmount
) {
}
