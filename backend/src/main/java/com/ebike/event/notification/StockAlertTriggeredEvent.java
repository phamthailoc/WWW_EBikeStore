package com.ebike.event.notification;

/** V22: a colour a customer was waiting for is back in stock. {@code language} is the customer's saved language ("vi" or "en"). */
public record StockAlertTriggeredEvent(
    Long userId,
    String productName,
    String colorName,
    String productSlug,
    String language
) {
}
