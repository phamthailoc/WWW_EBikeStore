package com.ebike.dto.order.request;

public record OrderCancellationRequest(String reason, String reviewNote) {
}
