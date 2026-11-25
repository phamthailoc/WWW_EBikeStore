package com.ebike.dto.order.request;

/** The new quantity of one line of an order, as the staff's order page sends it. */
public record OrderItemQuantityRequest(Integer quantity) {
}
