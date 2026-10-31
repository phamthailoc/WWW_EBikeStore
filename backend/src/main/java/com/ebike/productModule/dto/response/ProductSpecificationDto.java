package com.ebike.productModule.dto.response;

import java.math.BigDecimal;

public record ProductSpecificationDto(
    String modelCode,
    String brand,
    String vehicleType,
    String batteryType,
    BigDecimal batteryCapacityAh,
    BigDecimal maxSpeedKmh,
    BigDecimal maxRangeKm,
    Integer motorPowerWatts,
    String brakeType,
    String driveType,
    Integer warrantyMonths
) {
}
