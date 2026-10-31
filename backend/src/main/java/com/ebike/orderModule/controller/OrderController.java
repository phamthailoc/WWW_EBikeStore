package com.ebike.orderModule.controller;

import com.ebike.orderModule.dto.request.OrderCancellationRequest;
import com.ebike.orderModule.dto.request.OrderCreateRequest;
import com.ebike.orderModule.dto.request.OrderQuoteRequest;
import com.ebike.orderModule.dto.request.OrderStatusUpdateRequest;
import com.ebike.orderModule.dto.response.OrderQuoteResponse;
import com.ebike.orderModule.dto.response.OrderResponse;
import com.ebike.orderModule.service.OrderService;
import com.ebike.shared.constants.PermissionConstants;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> getOrders(
        Authentication authentication,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String paymentStatus,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Long showroomId
    ) {
        return orderService.getOrders(authentication, userId, status, paymentStatus, search, showroomId);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrderById(@PathVariable Long id, Authentication authentication) {
        return orderService.getOrderById(id, authentication);
    }

    @PostMapping("/quote")
    public OrderQuoteResponse quoteOrder(@RequestBody OrderQuoteRequest request) {
        return orderService.quoteOrder(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@RequestBody OrderCreateRequest request, Authentication authentication) {
        return orderService.createOrder(request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS + "')")
    public OrderResponse updateOrderStatus(@PathVariable Long id, @RequestBody OrderStatusUpdateRequest request) {
        return orderService.updateOrderStatus(id, request);
    }

    @PostMapping("/{id}/cancellation-request")
    @PreAuthorize("hasAuthority('" + PermissionConstants.Customer.ORDER_CANCEL_OWN + "')")
    public OrderResponse requestCancellation(
        @PathVariable Long id,
        @RequestBody(required = false) OrderCancellationRequest request,
        Authentication authentication
    ) {
        return orderService.requestCancellation(id, request, authentication);
    }

}
