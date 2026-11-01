package com.ebike.dto.manager.request;

public record ManagerShipmentUpdateRequest(
    String shipmentStatus,
    String trackingNumber
) {
}
