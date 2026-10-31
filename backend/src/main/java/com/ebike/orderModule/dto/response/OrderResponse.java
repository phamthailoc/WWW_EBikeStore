package com.ebike.orderModule.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    Long id,
    Long userId,
    String orderNumber,
    String status,
    BigDecimal subtotal,
    BigDecimal shippingFee,
    BigDecimal discountAmount,
    BigDecimal registrationFee,
    Boolean includeRegistrationService,
    BigDecimal totalAmount,
    String paymentMethod,
    String paymentStatus,
    String notes,
    String customerEmail,
    String cancellationReason,
    String cancellationReviewNote,
    String cancellationRequestedFromStatus,
    Long cancellationRequestedBy,
    LocalDateTime cancellationRequestedAt,
    LocalDateTime cancellationReviewedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<OrderItemResponse> items,
    ShipmentResponse shipment
) {
}
