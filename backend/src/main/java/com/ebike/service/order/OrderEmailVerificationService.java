package com.ebike.service.order;

import com.ebike.dto.order.request.OrderEmailOtpSendRequest;
import com.ebike.dto.order.request.OrderEmailOtpVerifyRequest;
import com.ebike.dto.order.response.OrderEmailOtpSendResponse;
import com.ebike.dto.order.response.OrderEmailOtpVerifyResponse;
import java.util.UUID;

public interface OrderEmailVerificationService {
    OrderEmailOtpSendResponse sendVerificationCode(OrderEmailOtpSendRequest request);

    OrderEmailOtpVerifyResponse verifyCode(OrderEmailOtpVerifyRequest request);

    void assertVerifiedForOrder(UUID verificationSessionId, String email);
}
