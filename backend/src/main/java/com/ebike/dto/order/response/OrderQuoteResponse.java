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
    // False when the admin switched the service off; checkout then hides the option.
    boolean registrationServiceAvailable,
    // State registration tax (lệ phí trước bạ) at the lowest and highest rate. Reference only: never in the total.
    BigDecimal registrationTaxLow,
    BigDecimal registrationTaxHigh
) {
}
