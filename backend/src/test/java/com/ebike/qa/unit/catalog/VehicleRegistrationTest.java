package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.service.product.VehicleRegistration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VehicleRegistrationTest {

    private static ProductSpecification spec(VehicleType type, String speed, Integer watts) {
        ProductSpecification s = Fixtures.spec(new Product(), type, "b");
        s.setMaxSpeedKmh(speed == null ? null : new BigDecimal(speed));
        s.setMotorPowerWatts(watts);
        return s;
    }

    @Test
    void nullSpecificationOrTypeNeedsNoRegistration() {
        assertThat(VehicleRegistration.requiresRegistration(null)).isFalse();
        assertThat(VehicleRegistration.requiresRegistration(spec(null, "99", 9999))).isFalse();
    }

    @Test
    void batteryAndAccessoryNeverNeedRegistration() {
        assertThat(VehicleRegistration.requiresRegistration(spec(VehicleType.BATTERY, "99", 9999))).isFalse();
        assertThat(VehicleRegistration.requiresRegistration(spec(VehicleType.ACCESSORY, "99", 9999))).isFalse();
    }

    @Test
    void motorbikeAlwaysNeedsRegistrationEvenWithoutFigures() {
        assertThat(VehicleRegistration.requiresRegistration(spec(VehicleType.E_MOTORBIKE, null, null))).isTrue();
        assertThat(VehicleRegistration.requiresRegistration(spec(VehicleType.E_MOTORBIKE, "10", 100))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "E_BIKE,25,250,false",
        "E_BIKE,25.01,250,true",
        "E_BIKE,25,251,true",
        "E_BIKE,24.99,100,false",
        "E_SCOOTER,25,250,false",
        "E_SCOOTER,40,200,true",
        "E_SCOOTER,20,1000,true",
        "E_BIKE,0,0,false",
        "E_BIKE,-5,-5,false"
    })
    void limitsAreTwentyFiveKmhAndTwoHundredFiftyWattsExclusive(VehicleType type, String speed, int watts, boolean expected) {
        assertThat(VehicleRegistration.requiresRegistration(spec(type, speed, watts))).isEqualTo(expected);
    }

    /**
     * An e-bike with unknown speed and power cannot be classified. Characterisation: the code answers "no registration", which is the
     * risky direction (a motorbike sold without a plate warning); documented here, see report.
     */
    @Test
    void unknownFiguresOnABikeAreTreatedAsNotRequired() {
        assertThat(VehicleRegistration.requiresRegistration(spec(VehicleType.E_BIKE, null, null))).isFalse();
        assertThat(VehicleRegistration.requiresRegistration(spec(VehicleType.E_SCOOTER, null, 300))).isTrue();
    }
}
