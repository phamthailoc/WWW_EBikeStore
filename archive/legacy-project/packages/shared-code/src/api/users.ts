import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type { UserAddressRequest, UserAddressResponse } from "../types";

export const userAPI = {
  listAddresses: async (userId: number | string) => {
    const response = await apiClient.get<UserAddressResponse[]>(API_ENDPOINTS.users.addresses(userId));
    return response.data;
  },
  createAddress: async (userId: number | string, payload: UserAddressRequest) => {
    const response = await apiClient.post<UserAddressResponse>(API_ENDPOINTS.users.addresses(userId), payload);
    return response.data;
  },
  updateAddress: async (userId: number | string, addressId: number | string, payload: UserAddressRequest) => {
    const response = await apiClient.put<UserAddressResponse>(API_ENDPOINTS.users.address(userId, addressId), payload);
    return response.data;
  },
  deleteAddress: async (userId: number | string, addressId: number | string) => {
    await apiClient.delete(API_ENDPOINTS.users.address(userId, addressId));
  }
};
