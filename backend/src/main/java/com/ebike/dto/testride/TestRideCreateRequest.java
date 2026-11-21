package com.ebike.dto.testride;

/** V23: a customer books a test ride. {@code slotStart} is a local date and time such as 2026-11-22T09:00. */
public record TestRideCreateRequest(
    Long productId,
    Long showroomId,
    String slotStart,
    String contactPhone,
    String note
) {
}
