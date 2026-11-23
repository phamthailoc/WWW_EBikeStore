package com.ebike.dto.savings;

import java.math.BigDecimal;

/**
 * V33: the answer of the savings calculator for one bike. {@code applicable} is false for a product that is not ridden (a battery pack, an accessory);
 * {@code calculable} is false when the bike's specification lacks the battery or range figures. In both cases {@code message} says why, in the page
 * language, and the figures are null. The assumptions are sent with the figures so that the page can show what the estimate rests on.
 */
public record SavingsResponse(
    boolean applicable,
    boolean calculable,
    String message,
    String vehicleType,
    Integer kmPerDay,
    Integer daysPerMonth,
    Integer monthlyKm,
    BigDecimal batteryKwh,
    BigDecimal kwhPer100Km,
    BigDecimal electricityCostPer100Km,
    BigDecimal petrolCostPer100Km,
    BigDecimal monthlyElectricityCost,
    BigDecimal monthlyPetrolCost,
    BigDecimal monthlySaving,
    BigDecimal yearlySaving,
    Boolean saves,
    BigDecimal electricityPricePerKwh,
    BigDecimal petrolPricePerLiter,
    BigDecimal petrolLitersPer100Km,
    int realRangePercent,
    int chargerEfficiencyPercent,
    int maxKmPerDay,
    int maxDaysPerMonth
) {
}
