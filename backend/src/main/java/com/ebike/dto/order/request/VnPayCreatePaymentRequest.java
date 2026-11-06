package com.ebike.dto.order.request;

public record VnPayCreatePaymentRequest(
    Long orderId,
    String bankCode,
    String language
) {
}
