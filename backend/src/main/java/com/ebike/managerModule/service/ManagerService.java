package com.ebike.managerModule.service;

import com.ebike.managerModule.dto.request.ManagerPaymentConfirmationRequest;
import com.ebike.managerModule.dto.request.ManagerProductStockUpdateRequest;
import com.ebike.managerModule.dto.request.ManagerShipmentUpdateRequest;
import com.ebike.managerModule.dto.response.ManagerCustomerResponse;
import com.ebike.managerModule.dto.response.ManagerDashboardResponse;
import com.ebike.managerModule.dto.response.ManagerPaymentResponse;
import com.ebike.managerModule.dto.response.ManagerRevenueReportResponse;
import com.ebike.orderModule.dto.request.OrderCancellationRequest;
import com.ebike.orderModule.dto.response.OrderResponse;
import com.ebike.productModule.dto.response.ProductSummaryDto;
import java.util.List;

public interface ManagerService {

    ManagerDashboardResponse getDashboard();

    List<ManagerPaymentResponse> getPayments(String paymentStatus, String paymentMethod, String search);

    ManagerPaymentResponse confirmPayLaterPayment(Long paymentId, ManagerPaymentConfirmationRequest request);

    OrderResponse approveOrderCancellation(Long orderId, OrderCancellationRequest request);

    OrderResponse rejectOrderCancellation(Long orderId, OrderCancellationRequest request);

    List<ManagerCustomerResponse> getCustomers(String search);

    ManagerRevenueReportResponse getRevenueReport(String period, String fromDate, String toDate);

    ProductSummaryDto updateProductStock(Long productId, ManagerProductStockUpdateRequest request);

    OrderResponse updateOrderShipment(Long orderId, ManagerShipmentUpdateRequest request);
}
