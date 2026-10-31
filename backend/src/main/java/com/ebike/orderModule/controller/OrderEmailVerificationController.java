package com.ebike.orderModule.controller;

import com.ebike.orderModule.dto.request.OrderEmailOtpSendRequest;
import com.ebike.orderModule.dto.request.OrderEmailOtpVerifyRequest;
import com.ebike.orderModule.dto.response.OrderEmailOtpSendResponse;
import com.ebike.orderModule.dto.response.OrderEmailOtpVerifyResponse;
import com.ebike.orderModule.service.OrderEmailVerificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/email-verification")
public class OrderEmailVerificationController {

    private final OrderEmailVerificationService verificationService;

    public OrderEmailVerificationController(OrderEmailVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/send")
    public OrderEmailOtpSendResponse send(@RequestBody OrderEmailOtpSendRequest request) {
        return verificationService.sendVerificationCode(request);
    }

    @PostMapping("/verify")
    public OrderEmailOtpVerifyResponse verify(@RequestBody OrderEmailOtpVerifyRequest request) {
        return verificationService.verifyCode(request);
    }
}
