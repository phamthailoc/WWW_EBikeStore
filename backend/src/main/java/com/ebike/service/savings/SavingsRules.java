package com.ebike.service.savings;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * V33: the arithmetic of the "how much does an electric bike save" calculator, free of the database so it can be tested alone.
 *
 * <p>Charging: the battery holds {@code Ah x V / 1000} kWh and the maker's range is a best case, so the real range is taken as
 * {@value #REAL_RANGE_PERCENT}% of it; the charger and the battery lose some energy, so the kWh drawn from the wall are the battery's divided by
 * {@value #CHARGER_EFFICIENCY_PERCENT}%. Petrol: litres per 100 km of a motorbike of the same class times the price of a litre. Money is whole dong,
 * rounded half up, and the yearly figure is twelve of the rounded monthly ones so that the numbers on the page add up.
 */
public final class SavingsRules {

    public static final int DEFAULT_KM_PER_DAY = 20;
    public static final int DEFAULT_DAYS_PER_MONTH = 26;
    public static final int MIN_KM_PER_DAY = 1;
    /** Far beyond what one rider covers in a day, so a typo in the box (such as 5000) is refused instead of shown as a huge saving. */
    public static final int MAX_KM_PER_DAY = 300;
    public static final int MIN_DAYS_PER_MONTH = 1;
    public static final int MAX_DAYS_PER_MONTH = 31;
    /** The range printed in the specification is measured in the best case; on the road it is lower. */
    public static final int REAL_RANGE_PERCENT = 80;
    /** Share of the energy drawn from the socket that reaches the battery. */
    public static final int CHARGER_EFFICIENCY_PERCENT = 85;

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final int MONTHS_PER_YEAR = 12;

    /** The battery and range of a bike as the specification states them. */
    public record Battery(BigDecimal capacityAh, BigDecimal voltageV, BigDecimal maxRangeKm) {
    }

    /** What the admin's pricing table says. */
    public record Prices(BigDecimal electricityPerKwh, BigDecimal petrolPerLiter, BigDecimal petrolLitersPer100Km) {
    }

    public record Result(
        BigDecimal batteryKwh,
        BigDecimal kwhPer100Km,
        int monthlyKm,
        BigDecimal electricityCostPer100Km,
        BigDecimal petrolCostPer100Km,
        BigDecimal monthlyElectricityCost,
        BigDecimal monthlyPetrolCost,
        BigDecimal monthlySaving,
        BigDecimal yearlySaving
    ) {
        public boolean saves() {
            return monthlySaving.signum() > 0;
        }
    }

    private SavingsRules() {
    }

    public static boolean isValidKmPerDay(int km) {
        return km >= MIN_KM_PER_DAY && km <= MAX_KM_PER_DAY;
    }

    public static boolean isValidDaysPerMonth(int days) {
        return days >= MIN_DAYS_PER_MONTH && days <= MAX_DAYS_PER_MONTH;
    }

    /** True when the specification has what the calculation needs: a battery size, a voltage and a range, each above zero. */
    public static boolean hasBatteryData(Battery battery) {
        return battery != null && positive(battery.capacityAh()) && positive(battery.voltageV()) && positive(battery.maxRangeKm());
    }

    /** The calculation; the caller has checked {@link #hasBatteryData}, the two numbers of the rider and that every price is above zero. */
    public static Result calculate(Battery battery, Prices prices, int kmPerDay, int daysPerMonth) {
        BigDecimal batteryKwh = battery.capacityAh().multiply(battery.voltageV()).divide(THOUSAND, MC);
        BigDecimal realRangeKm = battery.maxRangeKm().multiply(BigDecimal.valueOf(REAL_RANGE_PERCENT)).divide(HUNDRED, MC);
        BigDecimal batteryKwhPerKm = batteryKwh.divide(realRangeKm, MC);
        BigDecimal wallKwhPerKm = batteryKwhPerKm.multiply(HUNDRED).divide(BigDecimal.valueOf(CHARGER_EFFICIENCY_PERCENT), MC);

        BigDecimal electricityPerKm = wallKwhPerKm.multiply(prices.electricityPerKwh(), MC);
        BigDecimal petrolPerKm = prices.petrolLitersPer100Km().divide(HUNDRED, MC).multiply(prices.petrolPerLiter(), MC);

        int monthlyKm = kmPerDay * daysPerMonth;
        BigDecimal km = BigDecimal.valueOf(monthlyKm);
        BigDecimal monthlyElectricity = whole(electricityPerKm.multiply(km, MC));
        BigDecimal monthlyPetrol = whole(petrolPerKm.multiply(km, MC));
        BigDecimal monthlySaving = monthlyPetrol.subtract(monthlyElectricity);
        BigDecimal yearlySaving = monthlySaving.multiply(BigDecimal.valueOf(MONTHS_PER_YEAR));

        return new Result(
            batteryKwh.setScale(2, RoundingMode.HALF_UP),
            wallKwhPerKm.multiply(HUNDRED, MC).setScale(1, RoundingMode.HALF_UP),
            monthlyKm,
            whole(electricityPerKm.multiply(HUNDRED, MC)),
            whole(petrolPerKm.multiply(HUNDRED, MC)),
            monthlyElectricity,
            monthlyPetrol,
            monthlySaving,
            yearlySaving
        );
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal whole(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
