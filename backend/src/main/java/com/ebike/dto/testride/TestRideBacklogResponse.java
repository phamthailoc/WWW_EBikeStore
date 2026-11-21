package com.ebike.dto.testride;

/**
 * V23: what the staff must still handle, and no booking is in two counts: requests nobody confirmed (of an available showroom and bike), confirmed
 * rides whose whole hour has passed without being closed, confirmed rides the customer withdrew for a slot in the next 24 hours, and open bookings
 * of a showroom or bike that is no longer available whose hour is not over yet.
 */
public record TestRideBacklogResponse(
    long pending,
    long overdue,
    long customerCancelled,
    long unavailable
) {
}
