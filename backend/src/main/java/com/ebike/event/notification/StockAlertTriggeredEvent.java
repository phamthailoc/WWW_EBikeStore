package com.ebike.event.notification;

/** V22: a colour a customer was waiting for is back in stock. */
public record StockAlertTriggeredEvent(
    Long userId,
    String productName,
    String colorName,
    String productSlug
) {
}
