package com.ebike.event.notification;

/** V20: the staff moved a warranty request to a new step. */
public record WarrantyClaimStatusChangedEvent(
    Long userId,
    String productName,
    String frameNumber,
    String status
) {
}
