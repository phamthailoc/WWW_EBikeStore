import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type {
  ManagerCustomer,
  ManagerDashboard,
  ManagerPayment,
  ManagerPaymentConfirmationRequest,
  ManagerProductStockUpdateRequest,
  ManagerRevenueReport,
  Order,
  OrderCancellationRequest,
  Product
} from "../types";

export const managerAPI = {
  getDashboard: async () => {
    const response = await apiClient.get<ManagerDashboard>(API_ENDPOINTS.manager.dashboard);
    return response.data;
  },
  getPayments: async (params?: {
    paymentStatus?: string;
    paymentMethod?: string;
    search?: string;
  }) => {
    const response = await apiClient.get<ManagerPayment[]>(API_ENDPOINTS.manager.payments, { params });
    return response.data;
  },
  confirmPayment: async (paymentId: number, payload?: ManagerPaymentConfirmationRequest) => {
    const response = await apiClient.patch<ManagerPayment>(API_ENDPOINTS.manager.paymentConfirm(paymentId), payload);
    return response.data;
  },
  approveCancellation: async (orderId: string | number, payload?: OrderCancellationRequest) => {
    const response = await apiClient.patch<Order>(API_ENDPOINTS.manager.cancellationApprove(orderId), payload);
    return response.data;
  },
  rejectCancellation: async (orderId: string | number, payload?: OrderCancellationRequest) => {
    const response = await apiClient.patch<Order>(API_ENDPOINTS.manager.cancellationReject(orderId), payload);
    return response.data;
  },
  getCustomers: async (search?: string) => {
    const response = await apiClient.get<ManagerCustomer[]>(API_ENDPOINTS.manager.customers, {
      params: search ? { search } : undefined
    });
    return response.data;
  },
  updateProductStock: async (productId: number | string, payload: ManagerProductStockUpdateRequest) => {
    const response = await apiClient.patch<Product>(API_ENDPOINTS.manager.productStock(productId), payload);
    return response.data;
  },
  getRevenueReport: async (params: {
    period: "day" | "month" | "quarter" | "year" | "custom";
    from?: string;
    to?: string;
  }) => {
    const response = await apiClient.get<ManagerRevenueReport>(API_ENDPOINTS.manager.revenueReport, { params });
    return response.data;
  },
  updateOrderShipment: async (
    orderId: number | string,
    payload: { shipmentStatus: string; trackingNumber?: string }
  ) => {
    const response = await apiClient.patch<Order>(API_ENDPOINTS.manager.orderShipment(orderId), payload);
    return response.data;
  }
};
