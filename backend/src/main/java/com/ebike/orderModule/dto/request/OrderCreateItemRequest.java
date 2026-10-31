package com.ebike.orderModule.dto.request;

public record OrderCreateItemRequest(Long productId, Integer quantity, Long productVariantId) {
    public OrderCreateItemRequest(Long productId, Integer quantity) {
        this(productId, quantity, null);
    }
}
