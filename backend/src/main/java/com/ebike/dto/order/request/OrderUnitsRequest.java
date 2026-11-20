package com.ebike.dto.order.request;

import java.util.List;

/** The frame and engine numbers staff typed for an order: one entry per vehicle. */
public record OrderUnitsRequest(List<Entry> units) {
    public record Entry(Long orderItemId, String frameNumber, String engineNumber) {
    }
}
