import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type { AdminProductImage } from "../types";

export const productImageAPI = {
  listByProduct: async (productId: number | string) => {
    const response = await apiClient.get<AdminProductImage[]>(API_ENDPOINTS.productImages.listByProduct(productId));
    return response.data;
  },
  upload: async (
    productId: number,
    file: File,
    options?: { primaryImage?: boolean; altText?: string; sortOrder?: number }
  ) => {
    const formData = new FormData();
    formData.append("productId", String(productId));
    formData.append("file", file);
    if (options?.primaryImage) {
      formData.append("primaryImage", "true");
    }
    if (options?.altText) {
      formData.append("altText", options.altText);
    }
    if (options?.sortOrder != null) {
      formData.append("sortOrder", String(options.sortOrder));
    }

    const response = await apiClient.post<AdminProductImage>(API_ENDPOINTS.productImages.upload, formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });
    return response.data;
  },
  setPrimary: async (imageId: number | string) => {
    const formData = new FormData();
    formData.append("primaryImage", "true");
    const response = await apiClient.put<AdminProductImage>(API_ENDPOINTS.productImages.update(imageId), formData, {
      headers: { "Content-Type": "multipart/form-data" }
    });
    return response.data;
  },
  remove: async (imageId: number | string) => {
    await apiClient.delete(API_ENDPOINTS.productImages.remove(imageId));
  }
};
