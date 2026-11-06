package com.ebike.dto.order.request;

import java.util.List;

public record OrderQuoteRequest(Boolean includeRegistrationService, List<OrderCreateItemRequest> items, String discountCode) {
}
