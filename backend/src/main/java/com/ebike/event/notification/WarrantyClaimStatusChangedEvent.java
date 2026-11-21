package com.ebike.event.notification;

/** V20: the staff moved a warranty request to a new step, or (status {@code NOTE}) changed only the note the customer reads. */
public record WarrantyClaimStatusChangedEvent(
    Long userId,
    String productName,
    String frameNumber,
    String status
) {
}
