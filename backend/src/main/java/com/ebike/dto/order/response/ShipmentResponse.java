package com.ebike.dto.order.response;

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
    public String getDeliveryMethod() {
        return pickupShowroom == null ? "HOME_DELIVERY" : "STORE_PICKUP";
    }

    public String getProvince() {
        return pickupShowroom == null ? pickupDistrict : pickupShowroom.city();
    }
}
