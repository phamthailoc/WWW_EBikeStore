package com.ebike.event.notification;

/** V27: an order was just saved as DELIVERED (the bike was handed over or collected); guest orders count too. */
public record OrderDeliveredEvent(Long orderId) {
}
