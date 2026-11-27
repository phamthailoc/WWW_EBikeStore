package com.ebike.controller.order;

import com.ebike.dto.order.request.OrderEmailOtpSendRequest;
import com.ebike.dto.order.request.OrderEmailOtpVerifyRequest;
import com.ebike.dto.order.response.OrderEmailOtpSendResponse;
import com.ebike.dto.order.response.OrderEmailOtpVerifyResponse;
import com.ebike.service.order.OrderEmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
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
    public OrderEmailOtpSendResponse send(@RequestBody OrderEmailOtpSendRequest request, HttpServletRequest httpRequest) {
        // getRemoteAddr only, like the rate limiter: X-Forwarded-For is forgeable (production resolves the proxy through forward-headers-strategy=native)
        return verificationService.sendVerificationCode(request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/verify")
    public OrderEmailOtpVerifyResponse verify(@RequestBody OrderEmailOtpVerifyRequest request) {
        return verificationService.verifyCode(request);
    }
}
