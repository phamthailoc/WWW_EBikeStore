package com.ebike.service.order;

import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.request.OrderCancellationRequest;
import com.ebike.dto.order.request.OrderQuoteRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.dto.order.response.OrderQuoteResponse;
import com.ebike.dto.order.response.OrderResponse;
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

    OrderResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request, Authentication authentication);

    OrderResponse requestCancellation(Long id, OrderCancellationRequest request, Authentication authentication);
}
