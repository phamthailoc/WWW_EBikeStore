package com.ebike.dto.product.response;

import java.util.List;

public record ProductFilterOptionsDto(
    List<String> brands,
    List<String> vehicleTypes,
    List<String> batteryTypes
) {
}
