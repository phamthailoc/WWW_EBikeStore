package com.ebike.service.savings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.dto.savings.SavingsResponse;
import com.ebike.model.entity.admin.PricingRule;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.admin.PricingService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** V33: the savings of one bike, read from its specification and the admin's pricing table. */
class SavingsServiceTest {

    private ProductRepository products;
    private PricingRuleRepository rules;
    private SavingsService service;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        rules = mock(PricingRuleRepository.class);
        when(rules.findByCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        service = new SavingsService(products, new PricingService(rules));
    }

    private void bike(VehicleType type, String ah, String volts, String range, boolean active) {
        Product product = new Product();
        product.setSlug("amy");
        product.setActive(active);
        ProductSpecification spec = new ProductSpecification();
        spec.setVehicleType(type);
        spec.setBatteryCapacityAh(ah == null ? null : new BigDecimal(ah));
        spec.setBatteryVoltageV(volts == null ? null : new BigDecimal(volts));
        spec.setMaxRangeKm(range == null ? null : new BigDecimal(range));
        product.setSpecification(spec);
        when(products.findBySlug("amy")).thenReturn(Optional.of(product));
    }

    private void rule(String code, String amount, String status) {
        PricingRule rule = new PricingRule();
        rule.setCode(code);
        rule.setAmount(new BigDecimal(amount));
        rule.setStatus(status);
        when(rules.findByCode(code)).thenReturn(Optional.of(rule));
    }

    @Test
    void aBikeWithItsBatteryDataGetsAnEstimateWithTheDefaults() {
        bike(VehicleType.E_MOTORBIKE, "22", "48", "70", true);
        SavingsResponse answer = service.forProduct("amy", null, null);
        assertTrue(answer.applicable() && answer.calculable());
        assertEquals(20, answer.kmPerDay());
        assertEquals(26, answer.daysPerMonth());
        assertEquals(0, answer.electricityPricePerKwh().compareTo(new BigDecimal("2900")));
        assertEquals(0, answer.petrolLitersPer100Km().compareTo(new BigDecimal("1.8")));
        assertTrue(answer.saves());
    }

    @Test
    void thePriceTheAdminSetIsUsedAndAZeroOrInactiveOneFallsBack() {
        bike(VehicleType.E_SCOOTER, "20", "60", "80", true);
        rule(PricingService.SAVINGS_PETROL_PRICE, "30000", "ACTIVE");
        rule(PricingService.SAVINGS_ELECTRICITY_PRICE, "5000", "INACTIVE");
        rule(PricingService.SAVINGS_PETROL_USE_PREFIX + "E_SCOOTER", "0", "ACTIVE");
        SavingsResponse answer = service.forProduct("amy", 10, 30);
        assertEquals(0, answer.petrolPricePerLiter().compareTo(new BigDecimal("30000")));
        assertEquals(0, answer.electricityPricePerKwh().compareTo(new BigDecimal("2900")));
        assertEquals(0, answer.petrolLitersPer100Km().compareTo(new BigDecimal("2.0")));
    }

    @Test
    void aBikeWithoutBatteryDataIsApplicableButNotCalculable() {
        bike(VehicleType.E_BIKE, null, "48", "70", true);
        SavingsResponse answer = service.forProduct("amy", null, null);
        assertTrue(answer.applicable());
        assertFalse(answer.calculable());
        assertNull(answer.monthlySaving());
        assertTrue(answer.message() != null && !answer.message().isBlank());
    }

    @Test
    void aBatteryPackOrAccessoryIsNotApplicable() {
        bike(VehicleType.BATTERY, "22", "48", "70", true);
        SavingsResponse answer = service.forProduct("amy", null, null);
        assertFalse(answer.applicable());
        assertFalse(answer.calculable());
    }

    @Test
    void anUnknownOrHiddenBikeIs404() {
        when(products.findBySlug("missing")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.forProduct("missing", null, null)).getStatusCode());
        bike(VehicleType.E_SCOOTER, "20", "60", "80", false);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.forProduct("amy", null, null)).getStatusCode());
    }

    @Test
    void numbersOutOfRangeAre400() {
        bike(VehicleType.E_SCOOTER, "20", "60", "80", true);
        for (int[] bad : new int[][] {{0, 26}, {301, 26}, {20, 0}, {20, 32}, {-5, 26}}) {
            assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.forProduct("amy", bad[0], bad[1])).getStatusCode());
        }
    }
}
