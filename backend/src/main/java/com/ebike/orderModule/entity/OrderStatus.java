package com.ebike.orderModule.entity;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLATION_REQUESTED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
