package com.ebike.dto.manager.request;

public record ManagerShipmentUpdateRequest(
    String shipmentStatus,
    String trackingNumber,
    /** Why the shipment came back; required (and used as the cancellation reason) for RETURNED, ignored otherwise. */
    String reason
) {
    public ManagerShipmentUpdateRequest(String shipmentStatus, String trackingNumber) {
        this(shipmentStatus, trackingNumber, null);
    }
}
