package com.ebike.dto.product.response;

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
    Integer warrantyMonths,
    // True when the vehicle is a motorbike in law (over 25 km/h or 250 W) and must be registered and plated.
    boolean requiresRegistration
) {
}
