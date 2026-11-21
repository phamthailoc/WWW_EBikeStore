package com.ebike.dto.testride;

/**
 * V23: what the staff must still handle: requests nobody confirmed, confirmed rides whose time has passed without being closed, and confirmed rides
 * the customer withdrew for a slot in the next 24 hours.
 */
public record TestRideBacklogResponse(
    long pending,
    long overdue,
    long customerCancelled
) {
}
