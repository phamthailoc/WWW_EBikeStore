import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type { PaymentHistoryItem, VnPayCreatePaymentRequest, VnPayCreatePaymentResponse, VnPayReturnResponse } from "../types";

export const paymentAPI = {
  listHistory: async (status?: string) => {
    const response = await apiClient.get<PaymentHistoryItem[]>(API_ENDPOINTS.payments.history, {
      params: status ? { status } : undefined
    });
    return response.data;
  },

  createVnPayPayment: async (request: VnPayCreatePaymentRequest) => {
    const response = await apiClient.post<VnPayCreatePaymentResponse>(API_ENDPOINTS.payments.vnpayCreate, request);
    return response.data;
  },

  getVnPayReturnResult: async (queryString: string) => {
    const response = await apiClient.get<VnPayReturnResponse>(`${API_ENDPOINTS.payments.vnpayReturn}?${queryString}`);
    return response.data;
  }
};
