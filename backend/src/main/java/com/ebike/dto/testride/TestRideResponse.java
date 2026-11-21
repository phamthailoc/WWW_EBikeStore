package com.ebike.dto.testride;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V23: a booking as the customer and the staff read it. {@code canCancel} is for the customer, {@code nextSteps} for the staff: the steps
 * that may be taken right now (the order of the steps and the time of the ride both count). For a cancelled booking {@code cancelledBy} says
 * who cancelled (CUSTOMER, STORE, EXPIRED or ERASED; null on rows cancelled before V23 recorded it), and {@code cancelledByCustomerAt} is set
 * when the customer (or the erasure of the account) cancelled a booking the staff had already confirmed.
 */
public record TestRideResponse(
    Long id,
    Long productId,
    String productName,
    String productSlug,
    Long showroomId,
    String showroomName,
    String showroomAddress,
    LocalDateTime slotStart,
    String status,
    String contactPhone,
    String customerNote,
    String staffNote,
    String customerName,
    String customerEmail,
    boolean canCancel,
    List<String> nextSteps,
    LocalDateTime createdAt,
    String cancelledBy,
    LocalDateTime cancelledByCustomerAt
) {
}
