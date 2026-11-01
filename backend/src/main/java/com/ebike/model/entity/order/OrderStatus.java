package com.ebike.model.entity.order;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLATION_REQUESTED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
