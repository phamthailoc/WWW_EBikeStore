package com.ebike.orderModule.dto.request;

public record VnPayCreatePaymentRequest(
    Long orderId,
    String bankCode,
    String language
) {
}
