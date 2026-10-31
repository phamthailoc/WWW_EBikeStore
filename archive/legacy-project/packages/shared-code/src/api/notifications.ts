import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type { UnreadNotificationCountResponse, UserNotification } from "../types";

export const notificationAPI = {
  list: async () => {
    const response = await apiClient.get<UserNotification[]>(API_ENDPOINTS.notifications.list);
    return response.data;
  },
  unreadCount: async () => {
    const response = await apiClient.get<UnreadNotificationCountResponse>(API_ENDPOINTS.notifications.unreadCount);
    return response.data;
  },
  markAsRead: async (id: number | string) => {
    const response = await apiClient.patch<UserNotification>(API_ENDPOINTS.notifications.markRead(id));
    return response.data;
  },
  markAllAsRead: async () => {
    const response = await apiClient.patch<UserNotification[]>(API_ENDPOINTS.notifications.markAllRead);
    return response.data;
  }
};
