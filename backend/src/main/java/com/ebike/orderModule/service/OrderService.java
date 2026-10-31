package com.ebike.orderModule.service;

import com.ebike.orderModule.dto.request.OrderCreateRequest;
import com.ebike.orderModule.dto.request.OrderCancellationRequest;
import com.ebike.orderModule.dto.request.OrderQuoteRequest;
import com.ebike.orderModule.dto.request.OrderStatusUpdateRequest;
import com.ebike.orderModule.dto.response.OrderQuoteResponse;
import com.ebike.orderModule.dto.response.OrderResponse;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface OrderService {

    List<OrderResponse> getOrders(
        Authentication authentication,
        Long userId,
        String status,
        String paymentStatus,
        String search,
        Long showroomId
    );

    OrderResponse getOrderById(Long id, Authentication authentication);

    OrderQuoteResponse quoteOrder(OrderQuoteRequest request);

    OrderResponse createOrder(OrderCreateRequest request, Authentication authentication);

    OrderResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request);

    OrderResponse requestCancellation(Long id, OrderCancellationRequest request, Authentication authentication);
}
