package com.ebike.event.notification;

/** Published inside the checkout transaction; the confirmation email is sent after the commit. Guest orders are included. */
public record OrderPlacedEvent(Long orderId) {
}
