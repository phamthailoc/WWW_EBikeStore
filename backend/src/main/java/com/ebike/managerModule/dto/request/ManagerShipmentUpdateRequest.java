package com.ebike.managerModule.dto.request;

public record ManagerShipmentUpdateRequest(
    String shipmentStatus,
    String trackingNumber
) {
}
