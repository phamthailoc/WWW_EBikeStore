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

    // Used when a rule is missing or not yet in effect, so checkout keeps working on an older database.
    public static final BigDecimal DEFAULT_REGISTRATION_SERVICE = new BigDecimal("500000");
    public static final BigDecimal DEFAULT_HOME_DELIVERY_SAME_CITY = new BigDecimal("200000");
    public static final BigDecimal DEFAULT_HOME_DELIVERY_OTHER_PROVINCE = new BigDecimal("800000");
    public static final BigDecimal DEFAULT_REGISTRATION_TAX_RATE_CENTRAL = new BigDecimal("5");
    public static final BigDecimal DEFAULT_REGISTRATION_TAX_RATE_OTHER = new BigDecimal("2");

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

    public BigDecimal homeDeliveryPerBike(boolean provinceHasShowroom) {
        return provinceHasShowroom
            ? amount(HOME_DELIVERY_SAME_CITY, DEFAULT_HOME_DELIVERY_SAME_CITY)
            : amount(HOME_DELIVERY_OTHER_PROVINCE, DEFAULT_HOME_DELIVERY_OTHER_PROVINCE);
    }
}
