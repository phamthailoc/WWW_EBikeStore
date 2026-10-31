export type NotificationType = "ORDER_STATUS" | "PAYMENT_SUCCESS" | "PAYMENT_FAILED";

export interface UserNotification {
  id: number;
  type: NotificationType | string;
  title: string;
  message: string;
  targetUrl?: string | null;
  orderId?: number | null;
  paymentId?: number | null;
  read: boolean;
  readAt?: string | null;
  createdAt: string;
}

export interface UnreadNotificationCountResponse {
  unreadCount: number;
}
