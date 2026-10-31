import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type { Showroom } from "../types";

export const showroomAPI = {
  list: async (district?: string) => {
    const response = await apiClient.get<Showroom[]>(API_ENDPOINTS.showrooms.list, {
      params: district ? { district } : undefined
    });
    return response.data;
  }
};
