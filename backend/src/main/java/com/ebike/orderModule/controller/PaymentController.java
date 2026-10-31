package com.ebike.orderModule.controller;

import com.ebike.orderModule.dto.response.PaymentHistoryResponse;
import com.ebike.orderModule.service.PaymentHistoryService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentHistoryService paymentHistoryService;

    public PaymentController(PaymentHistoryService paymentHistoryService) {
        this.paymentHistoryService = paymentHistoryService;
    }

    @GetMapping("/history")
    public List<PaymentHistoryResponse> getMyPaymentHistory(
        Authentication authentication,
        @RequestParam(required = false) String status
    ) {
        return paymentHistoryService.getMyPaymentHistory(authentication, status);
    }
}
