package com.ebike.event.notification;

/** V20: a vehicle reached a maintenance milestone; {@code milestone} is the interval text such as P6M. */
public record MaintenanceDueEvent(
    Long userId,
    String productName,
    String frameNumber,
    String milestone
) {
}
