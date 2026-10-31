package com.ebike.orderModule.service;

import com.ebike.orderModule.dto.request.OrderEmailOtpSendRequest;
import com.ebike.orderModule.dto.request.OrderEmailOtpVerifyRequest;
import com.ebike.orderModule.dto.response.OrderEmailOtpSendResponse;
import com.ebike.orderModule.dto.response.OrderEmailOtpVerifyResponse;
import java.util.UUID;

public interface OrderEmailVerificationService {
    OrderEmailOtpSendResponse sendVerificationCode(OrderEmailOtpSendRequest request);

    OrderEmailOtpVerifyResponse verifyCode(OrderEmailOtpVerifyRequest request);

    void assertVerifiedForOrder(UUID verificationSessionId, String email);
}
