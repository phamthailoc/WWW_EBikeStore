package com.ebike.dto.warranty;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record WarrantyClaimResponse(
    Long id,
    Long unitId,
    String customerName,
    String email,
    String productName,
    String colorName,
    String frameNumber,
    String description,
    String status,
    boolean outOfWarranty,
    LocalDate warrantyEndsAt,
    String staffNote,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    // V27: the order of the vehicle, so staff can download its warranty card from the claim row
    Long orderId
) {
}
