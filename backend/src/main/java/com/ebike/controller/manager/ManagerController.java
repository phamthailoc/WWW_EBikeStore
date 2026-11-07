package com.ebike.controller.manager;

import com.ebike.dto.manager.request.ManagerPaymentConfirmationRequest;
import com.ebike.dto.manager.request.ManagerProductStockUpdateRequest;
import com.ebike.dto.manager.request.ManagerShipmentUpdateRequest;
import com.ebike.dto.manager.response.ManagerCustomerResponse;
import com.ebike.dto.manager.response.ManagerDashboardResponse;
import com.ebike.dto.manager.response.ManagerPaymentResponse;
import com.ebike.dto.manager.response.ManagerRevenueReportResponse;
import com.ebike.dto.order.request.OrderCancellationRequest;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.service.manager.ManagerService;
import com.ebike.common.constants.PermissionConstants;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manager")
public class ManagerController {

    private final ManagerService managerService;

    public ManagerController(ManagerService managerService) {
        this.managerService = managerService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('" + PermissionConstants.Reporting.DASHBOARD_VIEW + "')")
    public ManagerDashboardResponse getDashboard() {
        return managerService.getDashboard();
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.PAYMENT_VERIFY + "')")
    public List<ManagerPaymentResponse> getPayments(
        @RequestParam(required = false) String paymentStatus,
        @RequestParam(required = false) String paymentMethod,
        @RequestParam(required = false) String search
    ) {
        return managerService.getPayments(paymentStatus, paymentMethod, search);
    }

    @PatchMapping("/payments/{paymentId}/confirm")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.PAYMENT_VERIFY + "')")
    public ManagerPaymentResponse confirmPayLaterPayment(
        @PathVariable Long paymentId,
        @RequestBody(required = false) ManagerPaymentConfirmationRequest request
    ) {
        return managerService.confirmPayLaterPayment(paymentId, request);
    }

    @PatchMapping("/payments/{paymentId}/refund")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.PAYMENT_REFUND + "')")
    public ManagerPaymentResponse refundPayment(
        @PathVariable Long paymentId,
        @RequestBody(required = false) ManagerPaymentConfirmationRequest request
    ) {
        return managerService.refundPayment(paymentId, request);
    }

    @PatchMapping("/orders/{orderId}/cancellation/approve")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS + "')")
    public OrderResponse approveOrderCancellation(
        @PathVariable Long orderId,
        @RequestBody(required = false) OrderCancellationRequest request
    ) {
        return managerService.approveOrderCancellation(orderId, request);
    }

    @PatchMapping("/orders/{orderId}/cancellation/reject")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS + "')")
    public OrderResponse rejectOrderCancellation(
        @PathVariable Long orderId,
        @RequestBody(required = false) OrderCancellationRequest request
    ) {
        return managerService.rejectOrderCancellation(orderId, request);
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('" + PermissionConstants.UserManagement.USER_VIEW + "')")
    public List<ManagerCustomerResponse> getCustomers(@RequestParam(required = false) String search) {
        return managerService.getCustomers(search);
    }

    @GetMapping("/revenue-report")
    @PreAuthorize("hasAuthority('" + PermissionConstants.Reporting.REPORT_VIEW + "')")
    public ManagerRevenueReportResponse getRevenueReport(
        @RequestParam(defaultValue = "day") String period,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        return managerService.getRevenueReport(period, from, to);
    }

    @PatchMapping("/orders/{orderId}/shipment")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS + "')")
    public OrderResponse updateOrderShipment(
        @PathVariable Long orderId,
        @RequestBody ManagerShipmentUpdateRequest request
    ) {
        return managerService.updateOrderShipment(orderId, request);
    }

    @PatchMapping("/products/{productId}/stock")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_MANAGE_STOCK + "')")
    public ProductSummaryDto updateProductStock(
        @PathVariable Long productId,
        @RequestBody ManagerProductStockUpdateRequest request
    ) {
        return managerService.updateProductStock(productId, request);
    }

    @PatchMapping("/products/{productId}/variants/{variantId}/stock")
    @PreAuthorize("hasAuthority('" + PermissionConstants.ProductManagement.PRODUCT_MANAGE_STOCK + "')")
    public ProductSummaryDto updateVariantStock(
        @PathVariable Long productId,
        @PathVariable Long variantId,
        @RequestBody ManagerProductStockUpdateRequest request
    ) {
        return managerService.updateVariantStock(productId, variantId, request);
    }
}
