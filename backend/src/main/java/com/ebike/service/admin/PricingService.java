package com.ebike.service.admin;

import com.ebike.model.entity.admin.PricingRule;
import com.ebike.repository.admin.PricingRuleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Fees that the admin maintains in the pricing table (UC-24). Checkout reads them here instead of
 * using constants, so a change in the admin area applies to the next quote.
 */
@Service
public class PricingService {

    /** Store's fee per bike for handling the registration paperwork. Excludes state fees. */
    public static final String REGISTRATION_SERVICE = "REGISTRATION_SERVICE";
    /** Delivery fee per bike to an address in a province that has a showroom. */
    public static final String HOME_DELIVERY_SAME_CITY = "HOME_DELIVERY_SAME_CITY";
    /** Delivery fee per bike to a province without a showroom. */
    public static final String HOME_DELIVERY_OTHER_PROVINCE = "HOME_DELIVERY_OTHER_PROVINCE";
    /** Registration tax (lệ phí trước bạ), in percent, for central cities. Shown for reference only. */
    public static final String REGISTRATION_TAX_RATE_CENTRAL = "REGISTRATION_TAX_RATE_CENTRAL";
    /** Registration tax (lệ phí trước bạ), in percent, elsewhere. Shown for reference only. */
    public static final String REGISTRATION_TAX_RATE_OTHER = "REGISTRATION_TAX_RATE_OTHER";
    /** V24: yearly flat interest rate (percent) of a simulated instalment plan, one rule per term: INSTALLMENT_RATE_6, _9, _12. */
    public static final String INSTALLMENT_RATE_PREFIX = "INSTALLMENT_RATE_";

    /** V33: price of one kWh of household electricity (VND), used to estimate the cost of charging in the savings calculator. */
    public static final String SAVINGS_ELECTRICITY_PRICE = "SAVINGS_ELECTRICITY_PRICE";
    /** V33: price of one litre of petrol (VND). */
    public static final String SAVINGS_PETROL_PRICE = "SAVINGS_PETROL_PRICE";
    /** V33: litres of petrol a motorbike of the same class uses per 100 km; one rule per vehicle type: SAVINGS_PETROL_USE_E_SCOOTER, _E_BIKE, _E_MOTORBIKE. */
    public static final String SAVINGS_PETROL_USE_PREFIX = "SAVINGS_PETROL_USE_";
    /** V33: every rule of the calculator starts with this, so the admin's validation can recognise them. */
    public static final String SAVINGS_PREFIX = "SAVINGS_";

    // Used when a rule is missing or not yet in effect, so checkout keeps working on an older database.
    public static final BigDecimal DEFAULT_REGISTRATION_SERVICE = new BigDecimal("500000");
    public static final BigDecimal DEFAULT_HOME_DELIVERY_SAME_CITY = new BigDecimal("200000");
    public static final BigDecimal DEFAULT_HOME_DELIVERY_OTHER_PROVINCE = new BigDecimal("800000");
    public static final BigDecimal DEFAULT_REGISTRATION_TAX_RATE_CENTRAL = new BigDecimal("5");
    public static final BigDecimal DEFAULT_REGISTRATION_TAX_RATE_OTHER = new BigDecimal("2");

    public static final BigDecimal DEFAULT_SAVINGS_ELECTRICITY_PRICE = new BigDecimal("2900");
    public static final BigDecimal DEFAULT_SAVINGS_PETROL_PRICE = new BigDecimal("22000");

    /**
     * V33: the default litres per 100 km of the petrol motorbike a vehicle type is compared with. The electric motorbikes of the catalogue are the slow
     * 50cc class (35-45 km/h), so they are compared with a small petrol bike and use no more than the (stronger) scooters.
     */
    public static BigDecimal defaultPetrolUse(String vehicleType) {
        return switch (vehicleType) {
            case "E_BIKE", "E_MOTORBIKE" -> new BigDecimal("1.8");
            default -> new BigDecimal("2.0");
        };
    }

    /** V24: the default yearly rate (percent) of a term when its rule is missing or not yet in effect. */
    public static BigDecimal defaultInstallmentRate(int termMonths) {
        return switch (termMonths) {
            case 6 -> new BigDecimal("8");
            case 9 -> new BigDecimal("9");
            default -> new BigDecimal("10");
        };
    }

    private final PricingRuleRepository pricingRuleRepository;
    private final Clock clock;

    @Autowired
    public PricingService(PricingRuleRepository pricingRuleRepository) {
        this(pricingRuleRepository, Clock.systemDefaultZone());
    }

    public PricingService(PricingRuleRepository pricingRuleRepository, Clock clock) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.clock = clock;
    }

    /** The rule's amount when it is ACTIVE and already in effect; otherwise the fallback. */
    public BigDecimal amount(String code, BigDecimal fallback) {
        LocalDateTime now = LocalDateTime.now(clock);
        return pricingRuleRepository.findByCode(code)
            .filter(rule -> "ACTIVE".equals(rule.getStatus()))
            .filter(rule -> rule.getEffectiveFrom() == null || !rule.getEffectiveFrom().isAfter(now))
            .map(PricingRule::getAmount)
            .orElse(fallback);
    }

    public BigDecimal registrationServicePerBike() {
        return amount(REGISTRATION_SERVICE, DEFAULT_REGISTRATION_SERVICE);
    }

    /** The admin switches the service off by setting its rule INACTIVE; a missing rule keeps the default offer. */
    public boolean registrationServiceOffered() {
        return pricingRuleRepository.findByCode(REGISTRATION_SERVICE)
            .map(rule -> !"INACTIVE".equals(rule.getStatus()))
            .orElse(true);
    }

    public BigDecimal registrationTaxRateCentral() {
        return amount(REGISTRATION_TAX_RATE_CENTRAL, DEFAULT_REGISTRATION_TAX_RATE_CENTRAL);
    }

    public BigDecimal registrationTaxRateOther() {
        return amount(REGISTRATION_TAX_RATE_OTHER, DEFAULT_REGISTRATION_TAX_RATE_OTHER);
    }

    /** V24: the yearly flat rate (percent) of an instalment term. */
    public BigDecimal installmentRate(int termMonths) {
        return amount(INSTALLMENT_RATE_PREFIX + termMonths, defaultInstallmentRate(termMonths));
    }

    /**
     * V24: a term is offered with the rate the admin set, or with the default rate when it has no rule at all. A rule that is INACTIVE, SCHEDULED or not in
     * effect yet switches the term off: offering it would show the customer the default rate, not the figure the admin typed.
     */
    public boolean installmentTermOffered(int termMonths) {
        LocalDateTime now = LocalDateTime.now(clock);
        return pricingRuleRepository.findByCode(INSTALLMENT_RATE_PREFIX + termMonths)
            .map(rule -> "ACTIVE".equals(rule.getStatus()) && (rule.getEffectiveFrom() == null || !rule.getEffectiveFrom().isAfter(now)))
            .orElse(true);
    }

    /**
     * V33: a savings figure the admin set, or the default when the rule is missing, not ACTIVE, not in effect yet or not above zero: a price or a
     * consumption of zero would make the calculator show the whole petrol bill as a saving (or divide by nothing), so such a value is never used.
     */
    private BigDecimal positiveAmount(String code, BigDecimal fallback) {
        BigDecimal value = amount(code, fallback);
        return value != null && value.signum() > 0 ? value : fallback;
    }

    public BigDecimal savingsElectricityPricePerKwh() {
        return positiveAmount(SAVINGS_ELECTRICITY_PRICE, DEFAULT_SAVINGS_ELECTRICITY_PRICE);
    }

    public BigDecimal savingsPetrolPricePerLiter() {
        return positiveAmount(SAVINGS_PETROL_PRICE, DEFAULT_SAVINGS_PETROL_PRICE);
    }

    /** V33: litres of petrol per 100 km of the motorbike an electric vehicle of this type (E_SCOOTER, E_BIKE or E_MOTORBIKE) is compared with. */
    public BigDecimal savingsPetrolLitersPer100Km(String vehicleType) {
        return positiveAmount(SAVINGS_PETROL_USE_PREFIX + vehicleType, defaultPetrolUse(vehicleType));
    }

    public BigDecimal homeDeliveryPerBike(boolean provinceHasShowroom) {
        return provinceHasShowroom
            ? amount(HOME_DELIVERY_SAME_CITY, DEFAULT_HOME_DELIVERY_SAME_CITY)
            : amount(HOME_DELIVERY_OTHER_PROVINCE, DEFAULT_HOME_DELIVERY_OTHER_PROVINCE);
    }
}
