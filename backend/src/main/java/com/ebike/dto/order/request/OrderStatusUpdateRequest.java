package com.ebike.dto.order.request;

public record OrderStatusUpdateRequest(String status, String reason) {
    public OrderStatusUpdateRequest(String status) {
        this(status, null);
    }
}
