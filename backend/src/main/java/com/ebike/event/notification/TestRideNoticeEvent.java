package com.ebike.event.notification;

import java.time.LocalDateTime;

/**
 * V23: something happened to a test-ride booking that the customer should be told: {@code kind} is one of the constants of
 * TestRideMessages (CONFIRMED, CANCELLED by the store, NO_SHOW, EXPIRED, REMINDER).
 */
public record TestRideNoticeEvent(
    Long userId,
    String kind,
    String productName,
    String showroomName,
    String showroomAddress,
    LocalDateTime slotStart,
    String note
) {
}
