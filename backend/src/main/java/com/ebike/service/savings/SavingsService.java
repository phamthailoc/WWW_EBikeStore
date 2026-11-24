package com.ebike.service.savings;

import com.ebike.common.i18n.RequestLanguage;
import com.ebike.dto.savings.SavingsResponse;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.admin.PricingService;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * V33: "how much does an electric bike save". Public and read-only: it reads the bike's specification and the admin's pricing table every time (nothing is
 * cached, so a changed price shows on the next request) and keeps nothing about the visitor.
 */
@Service
@Transactional(readOnly = true)
public class SavingsService {

    /** The vehicle types that are ridden and have a petrol equivalent; a battery pack or an accessory has none. */
    private static final Set<String> RIDDEN = Set.of("E_SCOOTER", "E_BIKE", "E_MOTORBIKE");

    private final ProductRepository products;
    private final PricingService pricing;

    public SavingsService(ProductRepository products, PricingService pricing) {
        this.products = products;
        this.pricing = pricing;
    }

    /** Null {@code kmPerDay} or {@code daysPerMonth} means the default; a value outside the range is a 400. An unknown or hidden bike is a 404. */
    public SavingsResponse forProduct(String slug, Integer kmPerDay, Integer daysPerMonth) {
        Product product = products.findBySlug(slug == null ? "" : slug.trim())
            .filter(found -> Boolean.TRUE.equals(found.getActive()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Không tìm thấy sản phẩm."));
        return forProduct(product, kmPerDay, daysPerMonth);
    }

    /** True for a bike, scooter or motorbike (the types that have a petrol equivalent), false for a battery pack, an accessory or a product with no specification. */
    public static boolean isRidden(Product product) {
        ProductSpecification specification = product == null ? null : product.getSpecification();
        return specification != null && specification.getVehicleType() != null && RIDDEN.contains(specification.getVehicleType().name());
    }

    /** The same for a product that is already loaded (the chatbot has it); the caller has checked that it is visible. */
    public SavingsResponse forProduct(Product product, Integer kmPerDay, Integer daysPerMonth) {
        int km = kmPerDay == null ? SavingsRules.DEFAULT_KM_PER_DAY : kmPerDay;
        int days = daysPerMonth == null ? SavingsRules.DEFAULT_DAYS_PER_MONTH : daysPerMonth;
        if (!SavingsRules.isValidKmPerDay(km)) {
            throw bad("Số km mỗi ngày phải từ " + SavingsRules.MIN_KM_PER_DAY + " đến " + SavingsRules.MAX_KM_PER_DAY + ".");
        }
        if (!SavingsRules.isValidDaysPerMonth(days)) {
            throw bad("Số ngày đi mỗi tháng phải từ " + SavingsRules.MIN_DAYS_PER_MONTH + " đến " + SavingsRules.MAX_DAYS_PER_MONTH + ".");
        }

        ProductSpecification specification = product.getSpecification();
        String type = specification == null || specification.getVehicleType() == null ? null : specification.getVehicleType().name();
        if (type == null || !RIDDEN.contains(type)) {
            return unavailable(type, false, "Sản phẩm này không phải xe nên không có phần tính tiền tiết kiệm.");
        }
        SavingsRules.Battery battery = new SavingsRules.Battery(
            specification.getBatteryCapacityAh(), specification.getBatteryVoltageV(), specification.getMaxRangeKm());
        if (!SavingsRules.hasBatteryData(battery)) {
            return unavailable(type, true, "Xe này chưa có đủ thông số pin và quãng đường hợp lý nên chưa tính được. Vui lòng liên hệ cửa hàng để được tư vấn.");
        }

        SavingsRules.Prices prices = new SavingsRules.Prices(
            pricing.savingsElectricityPricePerKwh(), pricing.savingsPetrolPricePerLiter(), pricing.savingsPetrolLitersPer100Km(type));
        SavingsRules.Result result = SavingsRules.calculate(battery, prices, km, days);
        return new SavingsResponse(true, true, null, type, km, days, result.monthlyKm(), result.batteryKwh(), result.kwhPer100Km(),
            result.electricityCostPer100Km(), result.petrolCostPer100Km(), result.monthlyElectricityCost(), result.monthlyPetrolCost(),
            result.monthlySaving(), result.yearlySaving(), result.saves(),
            prices.electricityPerKwh(), prices.petrolPerLiter(), prices.petrolLitersPer100Km(),
            SavingsRules.REAL_RANGE_PERCENT, SavingsRules.CHARGER_EFFICIENCY_PERCENT, SavingsRules.MAX_KM_PER_DAY, SavingsRules.MAX_DAYS_PER_MONTH);
    }

    private static SavingsResponse unavailable(String type, boolean applicable, String message) {
        return new SavingsResponse(applicable, false, message, type, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, SavingsRules.REAL_RANGE_PERCENT, SavingsRules.CHARGER_EFFICIENCY_PERCENT, SavingsRules.MAX_KM_PER_DAY, SavingsRules.MAX_DAYS_PER_MONTH);
    }

    private static ResponseStatusException bad(String vietnamese) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, vietnamese);
    }
}
