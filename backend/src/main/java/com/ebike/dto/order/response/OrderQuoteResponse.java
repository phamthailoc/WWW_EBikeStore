package com.ebike.dto.order.response;

import java.math.BigDecimal;

public record OrderQuoteResponse(
    BigDecimal subtotal,
    BigDecimal shippingFee,
    BigDecimal discountAmount,
    BigDecimal registrationFee,
    BigDecimal totalAmount,
    // Store fee per bike for the registration service, so checkout can label the option before it is ticked.
    BigDecimal registrationServicePerBike,
    // True when the store helps with registration for this order: the admin has the service on, a bike in the
    // order needs a plate, and the buyer collects at a store (the owner declares on their own VNeID there).
    boolean registrationServiceAvailable,
    // State registration tax (lệ phí trước bạ) at the lowest and highest rate. Reference only: never in the total.
    BigDecimal registrationTaxLow,
    BigDecimal registrationTaxHigh,
    // True when at least one bike in the order must be registered and plated, whoever does the paperwork.
    boolean registrationRequired
) {
}
