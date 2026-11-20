package com.ebike.dto.warranty;

import java.time.LocalDate;

/** One delivered vehicle in the customer's warranty book. */
public record WarrantyUnitResponse(
    Long id,
    Long orderId,
    String orderNumber,
    String productName,
    String productSlug,
    String colorName,
    String frameNumber,
    String engineNumber,
    Integer warrantyMonths,
    LocalDate warrantyStartsAt,
    LocalDate warrantyEndsAt,
    String coverage,
    LocalDate nextMaintenanceAt,
    String claimStatus
) {
}
