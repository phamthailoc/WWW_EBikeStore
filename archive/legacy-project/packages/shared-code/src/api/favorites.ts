import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type { Product } from "../types";

export const favoritesAPI = {
  list: async () => {
    const response = await apiClient.get<Product[]>(API_ENDPOINTS.favorites.list);
    return response.data;
  },
  add: async (productId: number) => {
    const response = await apiClient.post<Product>(API_ENDPOINTS.favorites.add, { productId });
    return response.data;
  },
  remove: async (productId: number) => {
    await apiClient.delete(API_ENDPOINTS.favorites.remove(productId));
  }
};
