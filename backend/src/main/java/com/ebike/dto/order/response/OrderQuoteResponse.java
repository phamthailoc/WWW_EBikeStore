package com.ebike.dto.order.response;

import java.math.BigDecimal;

public record OrderQuoteResponse(
    BigDecimal subtotal,
    BigDecimal shippingFee,
    BigDecimal discountAmount,
    BigDecimal registrationFee,
    BigDecimal totalAmount,
    // Store fee per bike for the registration service, so checkout can label the option before it is ticked.
    BigDecimal registrationServicePerBike
) {
}
