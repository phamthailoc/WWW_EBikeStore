package com.ebike.orderModule.service;

import com.ebike.orderModule.dto.response.PaymentHistoryResponse;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface PaymentHistoryService {

    List<PaymentHistoryResponse> getMyPaymentHistory(Authentication authentication, String paymentStatus);
}
