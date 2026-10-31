import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type { Review, ReviewRequest } from "../types";

export const reviewsAPI = {
  listByProduct: async (productSlug: string) => {
    const response = await apiClient.get<Review[]>(API_ENDPOINTS.products.reviews(productSlug));
    return response.data;
  },
  create: async (productSlug: string, payload: ReviewRequest) => {
    const response = await apiClient.post<Review>(API_ENDPOINTS.products.reviews(productSlug), payload);
    return response.data;
  },
  update: async (reviewId: number | string, payload: ReviewRequest) => {
    const response = await apiClient.put<Review>(API_ENDPOINTS.reviews.update(reviewId), payload);
    return response.data;
  },
  remove: async (reviewId: number | string) => {
    await apiClient.delete(API_ENDPOINTS.reviews.remove(reviewId));
  }
};
