package com.ebike.dto.order.request;

import java.util.List;

/**
 * deliveryMethod is STORE_PICKUP (default) or HOME_DELIVERY; province is the delivery province and only
 * matters for HOME_DELIVERY.
 */
public record OrderQuoteRequest(
    Boolean includeRegistrationService,
    List<OrderCreateItemRequest> items,
    String discountCode,
    String deliveryMethod,
    String province
) {
    public OrderQuoteRequest(Boolean includeRegistrationService, List<OrderCreateItemRequest> items, String discountCode) {
        this(includeRegistrationService, items, discountCode, null, null);
    }
}
