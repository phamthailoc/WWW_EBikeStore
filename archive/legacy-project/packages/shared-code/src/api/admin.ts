import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type {
  AdminAccount,
  AdminAccountCreateRequest,
  AdminAuditLog,
  AdminPricingRule,
  AdminPromotion,
  AdminRolePermission
} from "../types";

export const adminAPI = {
  getPricingRules: async () => {
    const response = await apiClient.get<AdminPricingRule[]>(API_ENDPOINTS.admin.pricingRules);
    return response.data;
  },
  updatePricingRule: async (
    id: number | string,
    payload: { amount: number; status?: AdminPricingRule["status"]; effectiveFrom?: string | null }
  ) => {
    const response = await apiClient.patch<AdminPricingRule>(API_ENDPOINTS.admin.pricingRule(id), payload);
    return response.data;
  },
  getPromotions: async (status?: AdminPromotion["status"]) => {
    const response = await apiClient.get<AdminPromotion[]>(API_ENDPOINTS.admin.promotions, {
      params: status ? { status } : undefined
    });
    return response.data;
  },
  createPromotion: async (
    payload: Omit<AdminPromotion, "id" | "usageCount" | "updatedAt"> & { usageLimit: number }
  ) => {
    const response = await apiClient.post<AdminPromotion>(API_ENDPOINTS.admin.promotions, payload);
    return response.data;
  },
  updatePromotion: async (id: number | string, payload: Partial<AdminPromotion>) => {
    const response = await apiClient.patch<AdminPromotion>(API_ENDPOINTS.admin.promotion(id), payload);
    return response.data;
  },
  getAccounts: async (type?: "ADMIN" | "CUSTOMER") => {
    const response = await apiClient.get<AdminAccount[]>(API_ENDPOINTS.admin.accounts, {
      params: type ? { type } : undefined
    });
    return response.data;
  },
  createAccount: async (payload: AdminAccountCreateRequest) => {
    const response = await apiClient.post<AdminAccount>(API_ENDPOINTS.admin.accounts, payload);
    return response.data;
  },
  updateAccountRole: async (id: number | string, role: AdminAccountCreateRequest["role"]) => {
    const response = await apiClient.patch<AdminAccount>(API_ENDPOINTS.admin.accountRole(id), { role });
    return response.data;
  },
  updateAccountStatus: async (id: number | string, status: AdminAccount["status"]) => {
    const response = await apiClient.patch<AdminAccount>(API_ENDPOINTS.admin.accountStatus(id), { status });
    return response.data;
  },
  deleteAccount: async (id: number | string) => {
    await apiClient.delete(API_ENDPOINTS.admin.account(id));
  },
  getRoles: async () => {
    const response = await apiClient.get<AdminRolePermission[]>(API_ENDPOINTS.admin.roles);
    return response.data;
  },
  getAuditLogs: async () => {
    const response = await apiClient.get<AdminAuditLog[]>(API_ENDPOINTS.admin.auditLogs);
    return response.data;
  }
};
