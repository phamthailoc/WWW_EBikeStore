export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "CANCELLATION_REQUESTED"
  | "PROCESSING"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED";

export interface OrderItem {
  id?: number;
  productId: number;
  productName?: string;
  quantity: number;
  price?: number;
  unitPrice?: number;
  lineTotal?: number;
}

export interface VnPayCreatePaymentRequest {
  orderId: number;
  bankCode?: string;
  language?: "vn" | "en";
}

export interface VnPayCreatePaymentResponse {
  paymentUrl: string;
  paymentId: number;
  txnRef: string;
  amount: number;
  expireTime: string;
  orderNumber: string;
}

export interface VnPayReturnResponse {
  valid: boolean;
  success: boolean;
  responseCode?: string | null;
  txnRef?: string | null;
  amount?: number | null;
  transactionNo?: string | null;
  bankCode?: string | null;
  cardType?: string | null;
  paymentId?: number | null;
  orderId?: number | null;
  orderNumber?: string | null;
  paymentStatus?: string | null;
}

export type PaymentStatus = "PENDING" | "AUTHORIZED" | "PAID" | "FAILED" | "CANCELLED" | "REFUNDED";

export interface PaymentHistoryItem {
  id: number;
  orderId: number;
  orderNumber: string;
  transactionReference?: string | null;
  amount: number;
  currency: string;
  paymentMethod: "PAY_LATER" | "VNPAY" | string;
  paymentStatus: PaymentStatus | string;
  providerTxnId?: string | null;
  paidAt?: string | null;
  createdAt: string;
}

export interface Showroom {
  id: number;
  name: string;
  city: string;
  district: string;
  address: string;
  phone?: string | null;
  openingHours?: string | null;
  active: boolean;
}

export interface Shipment {
  id: number;
  shipmentStatus: string;
  recipientName: string;
  phoneNumber: string;
  recipientEmail?: string | null;
  pickupDistrict?: string | null;
  detailedAddress?: string | null;
  shippingAddress?: string | null;
  trackingNumber?: string | null;
  shippedAt?: string | null;
  deliveredAt?: string | null;
  pickupShowroom?: Showroom | null;
}

export interface Order {
  id: number;
  userId?: number | null;
  orderNumber: string;
  status: OrderStatus;
  subtotal: number;
  shippingFee: number;
  discountAmount: number;
  registrationFee: number;
  includeRegistrationService: boolean;
  totalAmount: number;
  paymentMethod?: "PAY_LATER" | "VNPAY" | string | null;
  paymentStatus?: string | null;
  notes?: string | null;
  customerEmail?: string | null;
  cancellationReason?: string | null;
  cancellationReviewNote?: string | null;
  cancellationRequestedFromStatus?: OrderStatus | string | null;
  cancellationRequestedBy?: number | null;
  cancellationRequestedAt?: string | null;
  cancellationReviewedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  items: OrderItem[];
  shipment?: Shipment | null;
}

export interface CreateOrderRequest {
  userId?: number | null;
  customerName: string;
  phoneNumber: string;
  customerEmail: string;
  emailVerificationSessionId: string;
  pickupShowroomId: number;
  detailedAddress: string;
  paymentMethod: "PAY_LATER" | "VNPAY";
  discountCode?: string;
  includeRegistrationService?: boolean;
  notes?: string;
  items: Array<{
    productId: number;
    quantity: number;
  }>;
}

export interface OrderQuoteRequest {
  includeRegistrationService?: boolean;
  discountCode?: string;
  items: Array<{
    productId: number;
    quantity: number;
  }>;
}

export interface OrderQuote {
  subtotal: number;
  shippingFee: number;
  discountAmount: number;
  registrationFee: number;
  totalAmount: number;
}

export interface OrderCancellationRequest {
  reason?: string;
  reviewNote?: string;
}

export interface OrderEmailOtpSendResponse {
  verificationSessionId: string;
  email: string;
  expiresAt: string;
  resendAfterSeconds: number;
  maxAttempts: number;
}

export interface OrderEmailOtpVerifyResponse {
  verificationSessionId: string;
  email: string;
  verified: boolean;
}
