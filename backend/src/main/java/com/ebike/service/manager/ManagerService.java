package com.ebike.service.manager;

import com.ebike.dto.manager.request.ManagerPaymentConfirmationRequest;
import com.ebike.dto.manager.request.ManagerProductStockUpdateRequest;
import com.ebike.dto.manager.request.ManagerShipmentUpdateRequest;
import com.ebike.dto.manager.response.ManagerCustomerResponse;
import com.ebike.dto.manager.response.ManagerDashboardResponse;
import com.ebike.dto.manager.response.ManagerPaymentResponse;
import com.ebike.dto.manager.response.ManagerRevenueReportResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.dto.product.response.ProductSummaryDto;
import java.util.List;

public interface ManagerService {

    ManagerDashboardResponse getDashboard();

    List<ManagerPaymentResponse> getPayments(String paymentStatus, String paymentMethod, String search);

    ManagerPaymentResponse confirmPayLaterPayment(Long paymentId, ManagerPaymentConfirmationRequest request);

    ManagerPaymentResponse refundPayment(Long paymentId, ManagerPaymentConfirmationRequest request);



    List<ManagerCustomerResponse> getCustomers(String search);

    ManagerRevenueReportResponse getRevenueReport(String period, String fromDate, String toDate);

    ProductSummaryDto updateProductStock(Long productId, ManagerProductStockUpdateRequest request);

    /** Sets the stock of one colour and recomputes the product's total from its colours. */
    ProductSummaryDto updateVariantStock(Long productId, Long variantId, ManagerProductStockUpdateRequest request);

    OrderResponse updateOrderShipment(Long orderId, ManagerShipmentUpdateRequest request);
}
