export const API_ENDPOINTS = {
  auth: {
    login: "/auth/login",
    enhancedLogin: "/auth/login/enhanced",
    register: "/auth/register",
    logout: "/auth/logout",
    session: "/auth/session",
    refresh: "/auth/refresh",
    profile: "/auth/profile",
    password: "/auth/change-password",
    userFromToken: "/auth/user-from-token"
  },
  products: {
    list: "/products",
    filterOptions: "/products/filter-options",
    detail: (id: string) => `/products/${id}`,
    related: (id: string) => `/products/${id}/related`,
    search: "/products/search",
    reviews: (slug: string) => `/products/${slug}/reviews`
  },
  reviews: {
    update: (reviewId: number | string) => `/reviews/${reviewId}`,
    remove: (reviewId: number | string) => `/reviews/${reviewId}`
  },
  favorites: {
    list: "/favorites",
    add: "/favorites",
    remove: (productId: number) => `/favorites/${productId}`
  },
  orders: {
    list: "/orders",
    create: "/orders",
    quote: "/orders/quote",
    emailOtpSend: "/orders/email-verification/send",
    emailOtpVerify: "/orders/email-verification/verify",
    detail: (id: string | number) => `/orders/${id}`,
    cancellationRequest: (id: string | number) => `/orders/${id}/cancellation-request`
  },
  users: {
    addresses: (userId: string | number) => `/users/${userId}/addresses`,
    address: (userId: string | number, addressId: string | number) => `/users/${userId}/addresses/${addressId}`
  },
  manager: {
    dashboard: "/manager/dashboard",
    payments: "/manager/payments",
    paymentConfirm: (paymentId: number | string) => `/manager/payments/${paymentId}/confirm`,
    productStock: (productId: number | string) => `/manager/products/${productId}/stock`,
    cancellationApprove: (orderId: number | string) => `/manager/orders/${orderId}/cancellation/approve`,
    cancellationReject: (orderId: number | string) => `/manager/orders/${orderId}/cancellation/reject`,
    customers: "/manager/customers",
    revenueReport: "/manager/revenue-report",
    orderShipment: (orderId: number | string) => `/manager/orders/${orderId}/shipment`
  },
  productImages: {
    listByProduct: (productId: number | string) => `/admin/product-images/by-product/${productId}`,
    upload: "/admin/product-images",
    update: (imageId: number | string) => `/admin/product-images/${imageId}`,
    remove: (imageId: number | string) => `/admin/product-images/${imageId}`
  },
  admin: {
    overview: "/admin/overview",
    pricingRules: "/admin/pricing-rules",
    pricingRule: (id: number | string) => `/admin/pricing-rules/${id}`,
    promotions: "/admin/promotions",
    promotion: (id: number | string) => `/admin/promotions/${id}`,
    accounts: "/admin/accounts",
    account: (id: number | string) => `/admin/accounts/${id}`,
    accountRole: (id: number | string) => `/admin/accounts/${id}/role`,
    accountStatus: (id: number | string) => `/admin/accounts/${id}/status`,
    roles: "/admin/roles",
    auditLogs: "/admin/audit-logs"
  },
  showrooms: {
    list: "/showrooms"
  },
  chat: {
    message: "/chatbot/ask",
    history: "/chat/history"
  },
  payments: {
    history: "/payments/history",
    vnpayCreate: "/payments/vnpay/create",
    vnpayReturn: "/payments/vnpay/return"
  }
  ,
  notifications: {
    list: "/notifications",
    unreadCount: "/notifications/unread-count",
    markRead: (id: number | string) => `/notifications/${id}/read`,
    markAllRead: "/notifications/read-all"
  }
} as const;
