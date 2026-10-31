package com.ebike.orderModule.dto.response;

import java.time.LocalDateTime;

public record ShipmentResponse(
    Long id,
    String shipmentStatus,
    String recipientName,
    String phoneNumber,
    String recipientEmail,
    String pickupDistrict,
    String detailedAddress,
    String shippingAddress,
    String trackingNumber,
    LocalDateTime shippedAt,
    LocalDateTime deliveredAt,
    ShowroomResponse pickupShowroom
) {
}
