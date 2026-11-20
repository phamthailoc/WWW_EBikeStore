package com.ebike.service.stock;

import com.ebike.dto.product.response.StockAlertResponse;

/** The answer to "tell me when this colour is back": {@code created} is false when the customer was already waiting. */
public record StockAlertSubscription(StockAlertResponse alert, boolean created) {
}
