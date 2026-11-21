package com.ebike.dto.warranty;

import java.time.LocalDate;

/** What anyone may learn from a frame number: the model and the warranty dates, never who owns the bike. */
public record WarrantyLookupResponse(
    String frameNumber,
    String productName,
    String colorName,
    Integer warrantyMonths,
    LocalDate warrantyStartsAt,
    LocalDate warrantyEndsAt,
    String coverage
) {
}
