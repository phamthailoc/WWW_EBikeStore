package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.controller.savings.SavingsController;
import com.ebike.dto.savings.SavingsResponse;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.admin.PricingService;
import com.ebike.service.savings.SavingsRules;
import com.ebike.service.savings.SavingsService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class SavingsTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static SavingsRules.Battery battery(String ah, String v, String km) {
        return new SavingsRules.Battery(ah == null ? null : d(ah), v == null ? null : d(v), km == null ? null : d(km));
    }

    // ---------------------------------------------------------------- rules

    @Test
    void constantsMatchTheDocumentedAssumptions() {
        assertThat(SavingsRules.REAL_RANGE_PERCENT).isEqualTo(80);
        assertThat(SavingsRules.CHARGER_EFFICIENCY_PERCENT).isEqualTo(85);
        assertThat(SavingsRules.DEFAULT_KM_PER_DAY).isEqualTo(20);
        assertThat(SavingsRules.DEFAULT_DAYS_PER_MONTH).isEqualTo(26);
    }

    @Test
    void workedExampleSixtyVoltTwentyAhEightyKm() {
        SavingsRules.Result r = SavingsRules.calculate(battery("20", "60", "80"),
            new SavingsRules.Prices(d("3000"), d("25000"), d("2")), 20, 26);
        assertThat(r.batteryKwh()).isEqualByComparingTo("1.20");           // 20 Ah x 60 V / 1000
        assertThat(r.kwhPer100Km()).isEqualByComparingTo("2.2");           // 1.2 / (80 km x 80%) x 100 / 85%
        assertThat(r.monthlyKm()).isEqualTo(520);
        assertThat(r.monthlyElectricityCost()).isEqualByComparingTo("34412");
        assertThat(r.monthlyPetrolCost()).isEqualByComparingTo("260000");  // 2 L/100km x 25000 x 520 km
        assertThat(r.monthlySaving()).isEqualByComparingTo("225588");
        assertThat(r.yearlySaving()).isEqualByComparingTo("2707056");       // twelve rounded months, not a re-rounded year
        assertThat(r.electricityCostPer100Km()).isEqualByComparingTo("6618");
        assertThat(r.petrolCostPer100Km()).isEqualByComparingTo("50000");
        assertThat(r.saves()).isTrue();
    }

    @Test
    void whenElectricityIsDearerThanPetrolTheSavingIsNegativeAndSavesIsFalse() {
        SavingsRules.Result r = SavingsRules.calculate(battery("20", "60", "80"),
            new SavingsRules.Prices(d("100000"), d("1000"), d("1")), 20, 26);
        assertThat(r.monthlySaving().signum()).isNegative();
        assertThat(r.saves()).isFalse();
    }

    @Test
    void equalCostsIsNotASaving() {
        SavingsRules.Result r = SavingsRules.calculate(battery("20", "60", "80"),
            new SavingsRules.Prices(d("0.000001"), d("0.000001"), d("0.000001")), 1, 1);
        assertThat(r.saves()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"1,true", "300,true", "0,false", "301,false", "-1,false", "2147483647,false", "-2147483648,false"})
    void kmPerDayBoundaries(int km, boolean valid) {
        assertThat(SavingsRules.isValidKmPerDay(km)).isEqualTo(valid);
    }

    @ParameterizedTest
    @CsvSource({"1,true", "30,true", "0,false", "31,false", "-3,false"})
    void daysPerMonthBoundaries(int days, boolean valid) {
        assertThat(SavingsRules.isValidDaysPerMonth(days)).isEqualTo(valid);
    }

    @Test
    void missingZeroOrNegativeBatteryDataIsRejected() {
        assertThat(SavingsRules.hasBatteryData(null)).isFalse();
        assertThat(SavingsRules.hasBatteryData(battery(null, "60", "80"))).isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("20", null, "80"))).isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("20", "60", null))).isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("0", "60", "80"))).isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("20", "-60", "80"))).isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("20", "60", "0"))).isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("20", "60", "80"))).isTrue();
    }

    @Test
    void implausibleConsumptionIsRefusedInBothDirections() {
        assertThat(SavingsRules.hasBatteryData(battery("20", "12", "80"))).as("12 V typo for a 60 V bike: 0.44 kWh/100km").isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("20", "60", "5"))).as("range typo: 35 kWh/100km").isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("1000", "1000", "80"))).isFalse();
    }

    @Test
    void consumptionLimitsAreInclusive() {
        // 1 kWh pack: kWh/100km = 100 / (range x 0.8 x 0.85); 200 km -> 0.735 (below 0.75), 160 km -> 0.919
        assertThat(SavingsRules.hasBatteryData(battery("10", "100", "200"))).isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("10", "100", "160"))).isTrue();
        // 18 km -> 8.17 (above 8), 19 km -> 7.74
        assertThat(SavingsRules.hasBatteryData(battery("10", "100", "18"))).isFalse();
        assertThat(SavingsRules.hasBatteryData(battery("10", "100", "19"))).isTrue();
    }

    @Test
    void extremeButValidInputsDoNotOverflow() {
        SavingsRules.Result r = SavingsRules.calculate(battery("20", "60", "80"),
            new SavingsRules.Prices(d("3000"), d("25000"), d("2")), SavingsRules.MAX_KM_PER_DAY, SavingsRules.MAX_DAYS_PER_MONTH);
        assertThat(r.monthlyKm()).isEqualTo(9000);
        assertThat(r.monthlyPetrolCost()).isEqualByComparingTo("4500000");
    }

    // ---------------------------------------------------------------- service

    private ProductRepository products;
    private PricingService pricing;
    private SavingsService service;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        pricing = mock(PricingService.class);
        service = new SavingsService(products, pricing);
        when(pricing.savingsElectricityPricePerKwh()).thenReturn(d("3000"));
        when(pricing.savingsPetrolPricePerLiter()).thenReturn(d("25000"));
        when(pricing.savingsPetrolLitersPer100Km("E_SCOOTER")).thenReturn(d("2"));
        when(pricing.savingsPetrolLitersPer100Km("E_BIKE")).thenReturn(d("1.5"));
        when(pricing.savingsPetrolLitersPer100Km("E_MOTORBIKE")).thenReturn(d("2.5"));
    }

    private Product bike(VehicleType type, String ah, String volt, String range) {
        Product product = Fixtures.product(1, "Bike", "100");
        ProductSpecification spec = Fixtures.spec(product, type, "b");
        spec.setBatteryCapacityAh(ah == null ? null : d(ah));
        spec.setBatteryVoltageV(volt == null ? null : d(volt));
        spec.setMaxRangeKm(range == null ? null : d(range));
        when(products.findBySlug("bike")).thenReturn(Optional.of(product));
        return product;
    }

    @Test
    void defaultsAreTwentyKmAndTwentySixDays() {
        bike(VehicleType.E_SCOOTER, "20", "60", "80");
        SavingsResponse r = service.forProduct("bike", null, null);
        assertThat(r.kmPerDay()).isEqualTo(20);
        assertThat(r.daysPerMonth()).isEqualTo(26);
        assertThat(r.calculable()).isTrue();
        assertThat(r.applicable()).isTrue();
        assertThat(r.monthlySaving()).isEqualByComparingTo("225588");
        assertThat(r.petrolLitersPer100Km()).isEqualByComparingTo("2");
        assertThat(r.realRangePercent()).isEqualTo(80);
        assertThat(r.chargerEfficiencyPercent()).isEqualTo(85);
    }

    @Test
    void petrolConsumptionDependsOnTheVehicleType() {
        bike(VehicleType.E_BIKE, "20", "60", "80");
        assertThat(service.forProduct("bike", null, null).petrolLitersPer100Km()).isEqualByComparingTo("1.5");
        bike(VehicleType.E_MOTORBIKE, "20", "60", "80");
        assertThat(service.forProduct("bike", null, null).petrolLitersPer100Km()).isEqualByComparingTo("2.5");
    }

    @Test
    void missingBatteryDataIsNotCalculableButStillApplicable() {
        bike(VehicleType.E_SCOOTER, null, "60", "80");
        SavingsResponse r = service.forProduct("bike", null, null);
        assertThat(r.applicable()).isTrue();
        assertThat(r.calculable()).isFalse();
        assertThat(r.message()).isNotBlank();
        assertThat(r.monthlySaving()).isNull();
    }

    @Test
    void batteryPackAndAccessoryAreNotApplicable() {
        bike(VehicleType.BATTERY, "20", "60", "80");
        SavingsResponse r = service.forProduct("bike", null, null);
        assertThat(r.applicable()).isFalse();
        assertThat(r.calculable()).isFalse();
        bike(VehicleType.ACCESSORY, null, null, null);
        assertThat(service.forProduct("bike", null, null).applicable()).isFalse();
    }

    @Test
    void productWithoutSpecificationIsNotApplicable() {
        Product product = Fixtures.product(1, "Bare", "1");
        when(products.findBySlug("bare")).thenReturn(Optional.of(product));
        assertThat(service.forProduct("bare", null, null).applicable()).isFalse();
        assertThat(SavingsService.isRidden(product)).isFalse();
        assertThat(SavingsService.isRidden(null)).isFalse();
    }

    @Test
    void outOfRangeInputsAreBadRequestsEvenForAnUnusableProduct() {
        bike(VehicleType.BATTERY, null, null, null);
        for (Integer[] bad : new Integer[][] {{0, 26}, {301, 26}, {-1, 26}, {20, 0}, {20, 31}, {20, -4}}) {
            assertThatThrownBy(() -> service.forProduct("bike", bad[0], bad[1]))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void boundaryInputsAreAccepted() {
        bike(VehicleType.E_SCOOTER, "20", "60", "80");
        assertThat(service.forProduct("bike", 1, 1).monthlyKm()).isEqualTo(1);
        assertThat(service.forProduct("bike", 300, 30).monthlyKm()).isEqualTo(9000);
    }

    @Test
    void unknownInactiveBlankOrNullSlugIs404() {
        Product product = bike(VehicleType.E_SCOOTER, "20", "60", "80");
        product.setActive(false);
        assertThatThrownBy(() -> service.forProduct("bike", null, null)).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.forProduct("ghost", null, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.forProduct((String) null, null, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.forProduct("   ", null, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void slugIsTrimmedBeforeLookup() {
        bike(VehicleType.E_SCOOTER, "20", "60", "80");
        assertThat(service.forProduct("  bike  ", null, null).applicable()).isTrue();
    }

    // ---------------------------------------------------------------- controller

    @Test
    void controllerNeverAllowsCachingBecausePricesChange() throws Exception {
        bike(VehicleType.E_SCOOTER, "20", "60", "80");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SavingsController(service)).build();
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/products/bike/savings?kmPerDay=30&daysPerMonth=10"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.monthlyKm").value(300));
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/products/bike/savings?kmPerDay=abc"))
            .andExpect(MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/products/bike/savings?kmPerDay=999"))
            .andExpect(MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/products/zzz/savings"))
            .andExpect(MockMvcResultMatchers.status().isNotFound());
    }
}
