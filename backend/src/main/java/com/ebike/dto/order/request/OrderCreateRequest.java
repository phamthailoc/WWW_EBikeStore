package com.ebike.dto.order.request;

import com.ebike.dto.installment.InstallmentRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderCreateRequest(
    Long userId,
    String customerName,
    String phoneNumber,
    String customerEmail,
    UUID emailVerificationSessionId,
    Long pickupShowroomId,
    String detailedAddress,
    String paymentMethod,
    String discountCode,
    BigDecimal shippingFee,
    BigDecimal discountAmount,
    Boolean includeRegistrationService,
    String notes,
    List<OrderCreateItemRequest> items,
    String deliveryMethod,
    String province,
    /** V24: the instalment application, only read when {@code paymentMethod} is INSTALLMENT. */
    InstallmentRequest installment
) {
    /** The request of an order that is not paid by instalments. */
    public OrderCreateRequest(
        Long userId, String customerName, String phoneNumber, String customerEmail, UUID emailVerificationSessionId,
        Long pickupShowroomId, String detailedAddress, String paymentMethod, String discountCode, BigDecimal shippingFee,
        BigDecimal discountAmount, Boolean includeRegistrationService, String notes, List<OrderCreateItemRequest> items,
        String deliveryMethod, String province
    ) {
        this(userId, customerName, phoneNumber, customerEmail, emailVerificationSessionId, pickupShowroomId, detailedAddress,
            paymentMethod, discountCode, shippingFee, discountAmount, includeRegistrationService, notes, items, deliveryMethod, province, null);
    }
}
