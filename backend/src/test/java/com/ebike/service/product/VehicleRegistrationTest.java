package com.ebike.service.product;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** An electric bicycle is at most 25 km/h and 250 W; anything faster or stronger is a motorbike that needs a plate. */
class VehicleRegistrationTest {

    private ProductSpecification spec(VehicleType type, String speed, Integer watts) {
        ProductSpecification spec = new ProductSpecification();
        spec.setVehicleType(type);
        spec.setMaxSpeedKmh(speed == null ? null : new BigDecimal(speed));
        spec.setMotorPowerWatts(watts);
        return spec;
    }

    @Test void atTheBicycleLimitNoRegistrationIsNeeded() {
        assertFalse(VehicleRegistration.requiresRegistration(spec(VehicleType.E_BIKE, "25", 250)));
        assertFalse(VehicleRegistration.requiresRegistration(spec(VehicleType.E_SCOOTER, "20", 200)));
    }

    @Test void overTheSpeedOrPowerLimitRegistrationIsNeeded() {
        assertTrue(VehicleRegistration.requiresRegistration(spec(VehicleType.E_BIKE, "26", 250)));
        assertTrue(VehicleRegistration.requiresRegistration(spec(VehicleType.E_BIKE, "25", 251)));
        assertTrue(VehicleRegistration.requiresRegistration(spec(VehicleType.E_SCOOTER, "50", 800)));
    }

    @Test void aMotorbikeAlwaysNeedsRegistration() {
        assertTrue(VehicleRegistration.requiresRegistration(spec(VehicleType.E_MOTORBIKE, null, null)));
        assertTrue(VehicleRegistration.requiresRegistration(spec(VehicleType.E_MOTORBIKE, "20", 200)));
    }

    @Test void batteriesAccessoriesAndUnknownSpecsNeverDo() {
        assertFalse(VehicleRegistration.requiresRegistration(spec(VehicleType.BATTERY, "99", 9999)));
        assertFalse(VehicleRegistration.requiresRegistration(spec(VehicleType.ACCESSORY, "99", 9999)));
        assertFalse(VehicleRegistration.requiresRegistration(spec(VehicleType.E_BIKE, null, null)));
        assertFalse(VehicleRegistration.requiresRegistration(null));
    }
}
