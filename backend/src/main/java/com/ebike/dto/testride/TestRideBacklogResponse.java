package com.ebike.dto.testride;

/** V23: what the staff must still handle: requests nobody confirmed, and confirmed rides whose time has passed without being closed. */
public record TestRideBacklogResponse(
    long pending,
    long overdue
) {
}
