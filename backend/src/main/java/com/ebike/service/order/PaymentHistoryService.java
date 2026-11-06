package com.ebike.service.order;

import com.ebike.dto.order.response.PaymentHistoryResponse;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface PaymentHistoryService {

    List<PaymentHistoryResponse> getMyPaymentHistory(Authentication authentication, String paymentStatus);
}
