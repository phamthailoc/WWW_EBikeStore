package com.ebike.dto.testride;

import java.time.LocalDateTime;

/** V23: one slot of a showroom day; {@code bookable} is false when it is full, too soon or already past. */
public record TestRideSlotResponse(
    LocalDateTime start,
    int capacity,
    int remaining,
    boolean bookable
) {
}
