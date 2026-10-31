package com.ebike.orderModule.service.impl;

import com.ebike.authModule.entity.User;
import com.ebike.authModule.repository.UserRepository;
import com.ebike.orderModule.dto.response.PaymentHistoryResponse;
import com.ebike.orderModule.entity.Order;
import com.ebike.orderModule.entity.Payment;
import com.ebike.orderModule.entity.PaymentStatus;
import com.ebike.orderModule.repository.PaymentRepository;
import com.ebike.orderModule.service.PaymentHistoryService;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentHistoryServiceImpl implements PaymentHistoryService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public PaymentHistoryServiceImpl(PaymentRepository paymentRepository, UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getMyPaymentHistory(Authentication authentication, String paymentStatus) {
        User currentUser = getAuthenticatedUser(authentication);
        PaymentStatus parsedPaymentStatus = parsePaymentStatus(paymentStatus);

        return paymentRepository.findHistoryByUserId(currentUser.getId(), parsedPaymentStatus).stream()
            .map(this::toResponse)
            .toList();
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
            .or(() -> userRepository.findByEmail(authentication.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private PaymentStatus parsePaymentStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment status filter");
        }
    }

    private PaymentHistoryResponse toResponse(Payment payment) {
        Order order = payment.getOrder();
        return new PaymentHistoryResponse(
            payment.getId(),
            order.getId(),
            order.getOrderNumber(),
            payment.getTransactionReference(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getPaymentMethod().name(),
            payment.getPaymentStatus().name(),
            payment.getProviderTxnId(),
            payment.getPaidAt(),
            order.getCreatedAt()
        );
    }
}
