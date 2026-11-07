package com.ebike.service.product;

import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;
import java.math.BigDecimal;

/**
 * Which vehicles must be registered and plated. An electric bicycle is limited to 25 km/h and 250 W; a vehicle
 * faster or stronger than that is a motorbike in law, whatever the product is called.
 */
public final class VehicleRegistration {

    public static final BigDecimal MAX_BICYCLE_SPEED_KMH = new BigDecimal("25");
    public static final int MAX_BICYCLE_POWER_WATTS = 250;

    private VehicleRegistration() {
    }

    public static boolean requiresRegistration(ProductSpecification specification) {
        if (specification == null || specification.getVehicleType() == null) {
            return false;
        }
        VehicleType type = specification.getVehicleType();
        if (type == VehicleType.BATTERY || type == VehicleType.ACCESSORY) {
            return false;
        }
        if (type == VehicleType.E_MOTORBIKE) {
            return true;
        }
        BigDecimal speed = specification.getMaxSpeedKmh();
        Integer power = specification.getMotorPowerWatts();
        return (speed != null && speed.compareTo(MAX_BICYCLE_SPEED_KMH) > 0)
            || (power != null && power > MAX_BICYCLE_POWER_WATTS);
    }
}
