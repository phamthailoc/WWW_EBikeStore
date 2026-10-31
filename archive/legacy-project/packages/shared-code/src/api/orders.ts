import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type {
  CreateOrderRequest,
  Order,
  OrderCancellationRequest,
  OrderEmailOtpSendResponse,
  OrderEmailOtpVerifyResponse,
  OrderQuote,
  OrderQuoteRequest
} from "../types";

export type OrderListParams = {
  userId?: number;
  status?: string;
  paymentStatus?: string;
  search?: string;
  showroomId?: number;
};

export const orderAPI = {
  list: async (params?: number | OrderListParams) => {
    const resolvedParams = typeof params === "number" ? { userId: params } : params;
    const response = await apiClient.get<Order[]>(API_ENDPOINTS.orders.list, {
      params: resolvedParams
    });
    return response.data;
  },
  getDetail: async (id: string) => {
    const response = await apiClient.get<Order>(API_ENDPOINTS.orders.detail(id));
    return response.data;
  },
  quote: async (payload: OrderQuoteRequest) => {
    const response = await apiClient.post<OrderQuote>(API_ENDPOINTS.orders.quote, payload);
    return response.data;
  },
  create: async (payload: CreateOrderRequest) => {
    const response = await apiClient.post<Order>(API_ENDPOINTS.orders.create, payload);
    return response.data;
  },
  sendEmailOtp: async (email: string) => {
    const response = await apiClient.post<OrderEmailOtpSendResponse>(API_ENDPOINTS.orders.emailOtpSend, { email });
    return response.data;
  },
  verifyEmailOtp: async (verificationSessionId: string, code: string) => {
    const response = await apiClient.post<OrderEmailOtpVerifyResponse>(API_ENDPOINTS.orders.emailOtpVerify, {
      verificationSessionId,
      code
    });
    return response.data;
  },
  requestCancellation: async (id: string | number, payload?: OrderCancellationRequest) => {
    const response = await apiClient.post<Order>(API_ENDPOINTS.orders.cancellationRequest(id), payload);
    return response.data;
  }
};
