package com.ebike.service.savings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** V33: the arithmetic of the savings calculator. */
class SavingsRulesTest {

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private static final SavingsRules.Battery BATTERY = new SavingsRules.Battery(n("22"), n("48"), n("70"));
    private static final SavingsRules.Prices PRICES = new SavingsRules.Prices(n("2900"), n("22000"), n("2.5"));

    @Test
    void petrolIsLitresTimesPriceTimesDistance() {
        SavingsRules.Result result = SavingsRules.calculate(BATTERY, PRICES, 20, 26);
        assertEquals(520, result.monthlyKm());
        // 2.5 l/100 km x 22,000 = 550 per km; 520 km = 286,000
        assertEquals(0, result.monthlyPetrolCost().compareTo(n("286000")));
        assertEquals(0, result.petrolCostPer100Km().compareTo(n("55000")));
    }

    @Test
    void chargingUsesRealRangeAndChargerLoss() {
        SavingsRules.Result result = SavingsRules.calculate(BATTERY, PRICES, 20, 26);
        // 1.056 kWh battery / (70 x 0.8 = 56 km) / 0.85 = 0.022185 kWh per km = 2.2 kWh per 100 km
        assertEquals(0, result.batteryKwh().compareTo(n("1.06")));
        assertEquals(0, result.kwhPer100Km().compareTo(n("2.2")));
        // 2.2185 kWh x 2,900 = about 6,434 per 100 km; 520 km = about 33,454
        assertTrue(result.electricityCostPer100Km().subtract(n("6434")).abs().compareTo(n("2")) <= 0, result.electricityCostPer100Km().toPlainString());
        assertTrue(result.monthlyElectricityCost().subtract(n("33454")).abs().compareTo(n("10")) <= 0, result.monthlyElectricityCost().toPlainString());
    }

    @Test
    void savingIsPetrolMinusChargingAndYearIsTwelveMonths() {
        SavingsRules.Result result = SavingsRules.calculate(BATTERY, PRICES, 20, 26);
        assertEquals(0, result.monthlySaving().compareTo(result.monthlyPetrolCost().subtract(result.monthlyElectricityCost())));
        assertEquals(0, result.yearlySaving().compareTo(result.monthlySaving().multiply(n("12"))));
        assertTrue(result.saves());
    }

    @Test
    void aDearerElectricityPriceThanPetrolShowsNoSaving() {
        SavingsRules.Prices dear = new SavingsRules.Prices(n("200000"), n("22000"), n("2"));
        SavingsRules.Result result = SavingsRules.calculate(BATTERY, dear, 20, 26);
        assertFalse(result.saves());
        assertTrue(result.monthlySaving().signum() < 0);
    }

    @Test
    void theFigureGrowsWithTheDistance() {
        SavingsRules.Result near = SavingsRules.calculate(BATTERY, PRICES, 5, 30);
        SavingsRules.Result far = SavingsRules.calculate(BATTERY, PRICES, 50, 30);
        assertTrue(far.monthlySaving().compareTo(near.monthlySaving()) > 0);
    }

    @Test
    void theBatteryDataMustBeCompleteAndAboveZero() {
        assertTrue(SavingsRules.hasBatteryData(BATTERY));
        assertFalse(SavingsRules.hasBatteryData(null));
        assertFalse(SavingsRules.hasBatteryData(new SavingsRules.Battery(null, n("48"), n("70"))));
        assertFalse(SavingsRules.hasBatteryData(new SavingsRules.Battery(n("22"), n("0"), n("70"))));
        assertFalse(SavingsRules.hasBatteryData(new SavingsRules.Battery(n("22"), n("48"), n("-1"))));
    }

    @Test
    void theRidersNumbersAreRangeChecked() {
        assertTrue(SavingsRules.isValidKmPerDay(1));
        assertTrue(SavingsRules.isValidKmPerDay(SavingsRules.MAX_KM_PER_DAY));
        assertFalse(SavingsRules.isValidKmPerDay(0));
        assertFalse(SavingsRules.isValidKmPerDay(SavingsRules.MAX_KM_PER_DAY + 1));
        assertTrue(SavingsRules.isValidDaysPerMonth(31));
        assertFalse(SavingsRules.isValidDaysPerMonth(32));
        assertFalse(SavingsRules.isValidDaysPerMonth(0));
    }
}
