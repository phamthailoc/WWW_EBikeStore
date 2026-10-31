package com.ebike.orderModule.dto.response;

import java.math.BigDecimal;

public record OrderQuoteResponse(
    BigDecimal subtotal,
    BigDecimal shippingFee,
    BigDecimal discountAmount,
    BigDecimal registrationFee,
    BigDecimal totalAmount
) {
}
