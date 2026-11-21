package com.ebike.dto.testride;

/** V23: the staff move a booking to a new step, with a note the customer reads; a cancellation needs the note. */
public record TestRideUpdateRequest(
    String status,
    String staffNote
) {
}
