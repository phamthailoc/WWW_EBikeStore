import type { Order } from "./order";

export interface ManagerDashboard {
  totalOrders: number;
  pendingOrders: number;
  unpaidPayLaterOrders: number;
  todayRevenue: number;
  weekRevenue: number;
  monthRevenue: number;
  recentOrders: Order[];
}

export interface ManagerPayment {
  id: number;
  orderId: number;
  orderNumber: string;
  customerName?: string | null;
  customerEmail?: string | null;
  amount: number;
  currency: string;
  paymentMethod: string;
  paymentStatus: string;
  transactionReference?: string | null;
  providerTxnId?: string | null;
  note?: string | null;
  paidAt?: string | null;
  orderCreatedAt: string;
}

export interface ManagerCustomer {
  id: number;
  username: string;
  email: string;
  firstName?: string | null;
  lastName?: string | null;
  active: boolean;
  verified: boolean;
  orderCount: number;
  totalSpent: number;
  createdAt: string;
}

export interface ManagerPaymentConfirmationRequest {
  providerTxnId?: string;
  note?: string;
}

export interface ManagerProductStockUpdateRequest {
  stockQuantity: number;
}

export interface ManagerRevenuePeriodPoint {
  label: string;
  orderCount: number;
  revenue: number;
}

export interface ManagerTopProduct {
  productId: number;
  productName: string;
  quantitySold: number;
  revenue: number;
}

export interface ManagerRevenueReport {
  periodType: string;
  fromDate: string;
  toDate: string;
  totalOrders: number;
  successfulOrders: number;
  totalRevenue: number;
  breakdown: ManagerRevenuePeriodPoint[];
  topProducts: ManagerTopProduct[];
  slowProducts: ManagerTopProduct[];
}
