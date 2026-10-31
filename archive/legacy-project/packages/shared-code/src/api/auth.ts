import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type { ChangePasswordRequest, EnhancedAuthResponse, LoginRequest, RegisterRequest, UpdateProfileRequest, UserProfileResponse } from "../types";

export const authAPI = {
  login: async (payload: LoginRequest) => {
    const response = await apiClient.post<EnhancedAuthResponse>(API_ENDPOINTS.auth.enhancedLogin, payload);
    return response.data;
  },
  register: async (payload: RegisterRequest) => {
    const response = await apiClient.post(API_ENDPOINTS.auth.register, payload);
    return response.data;
  },
  logout: async () => {
    await apiClient.post(API_ENDPOINTS.auth.logout);
  },
  getSession: async () => {
    const response = await apiClient.get<UserProfileResponse | null>(API_ENDPOINTS.auth.session, {
      validateStatus: (status) => status === 200 || status === 204
    });
    return response.status === 204 ? null : response.data;
  },
  getProfile: async (usernameOrEmail?: string) => {
    const response = await apiClient.get<UserProfileResponse>(API_ENDPOINTS.auth.profile, {
      params: usernameOrEmail ? { usernameOrEmail } : undefined
    });
    return response.data;
  },
  updateProfile: async (payload: UpdateProfileRequest) => {
    const response = await apiClient.put<UserProfileResponse>(API_ENDPOINTS.auth.profile, payload);
    return response.data;
  },
  changePassword: async (payload: ChangePasswordRequest) => {
    await apiClient.post(API_ENDPOINTS.auth.password, payload);
  },
  getUserFromToken: async () => {
    const response = await apiClient.get<string>(API_ENDPOINTS.auth.userFromToken);
    return response.data;
  }
};
