package com.ebike.orderModule.dto.request;

import java.util.List;

public record OrderQuoteRequest(Boolean includeRegistrationService, List<OrderCreateItemRequest> items, String discountCode) {
}
