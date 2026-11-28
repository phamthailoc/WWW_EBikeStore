package com.ebike.controller.order;

import com.ebike.dto.order.request.GuestOrderLinkRequest;
import com.ebike.dto.order.request.OrderCancellationRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.request.OrderQuoteRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.dto.order.response.GuestOrderLinkResponse;
import com.ebike.dto.order.response.OrderQuoteResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.service.order.OrderService;
import com.ebike.common.constants.PermissionConstants;
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
    private final com.ebike.service.cart.SessionCartService sessionCart;

    public OrderController(OrderService orderService, com.ebike.service.cart.SessionCartService sessionCart) {
        this.sessionCart = sessionCart;
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> getOrders(
        Authentication authentication,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String paymentStatus,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Long showroomId,
        @RequestParam(required = false) Integer limit
    ) {
        return orderService.getOrders(authentication, userId, status, paymentStatus, search, showroomId, limit == null ? 2000 : limit);
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
    public OrderResponse createOrder(@RequestBody OrderCreateRequest request, Authentication authentication,
                                    @org.springframework.web.bind.annotation.RequestHeader(value = "X-Cart-Checkout", required = false) String fromCart,
                                    jakarta.servlet.http.HttpServletRequest http) {
        OrderResponse created = orderService.createOrder(request, authentication);
        // Checkout of the cart (not "buy now") takes the bought quantities out of the session cart. The order exists already, so a failure here must not fail the request.
        if ("1".equals(fromCart)) {
            try {
                sessionCart.removePurchased(http.getSession(false), request.items());
            } catch (RuntimeException exception) {
                org.slf4j.LoggerFactory.getLogger(OrderController.class).warn("Could not update the session cart after order {}", created.id(), exception);
            }
        }
        return created;
    }

    @PatchMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS + "')")
    public OrderResponse updateItemQuantity(@PathVariable Long id, @PathVariable Long itemId,
                                            @RequestBody com.ebike.dto.order.request.OrderItemQuantityRequest request, Authentication authentication) {
        return orderService.updateItemQuantity(id, itemId, request == null ? null : request.quantity(), authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS + "')")
    public OrderResponse updateOrderStatus(@PathVariable Long id, @RequestBody OrderStatusUpdateRequest request, Authentication authentication) {
        return orderService.updateOrderStatus(id, request, authentication);
    }

    @PostMapping("/guest-orders/link")
    @PreAuthorize("hasAuthority('" + PermissionConstants.Customer.ORDER_VIEW_OWN + "')")
    public GuestOrderLinkResponse linkGuestOrders(
        @RequestBody(required = false) GuestOrderLinkRequest request,
        Authentication authentication
    ) {
        return orderService.linkGuestOrders(request, authentication);
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
