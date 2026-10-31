export type AdminPricingRule = {
  id: number;
  code: string;
  name: string;
  description: string;
  amount: number;
  unit: string;
  status: "ACTIVE" | "SCHEDULED" | "INACTIVE";
  effectiveFrom?: string | null;
  updatedAt?: string | null;
};

export type AdminPromotion = {
  id: number;
  code: string;
  campaignName: string;
  discountType: "PERCENTAGE" | "FIXED_AMOUNT";
  discountValue: number;
  maxDiscountAmount?: number | null;
  usageCount: number;
  usageLimit: number;
  status: "LIVE" | "WAITING" | "EXPIRED" | "DISABLED";
  startsAt?: string | null;
  endsAt?: string | null;
  updatedAt?: string | null;
};

export type AdminAccount = {
  id: number;
  name: string;
  username: string;
  email: string;
  accountType: "ADMIN" | "CUSTOMER";
  role: "ADMIN" | "OPERATOR" | "SUPPORT" | "MAINTENANCE" | "CUSTOMER";
  status: "ACTIVE" | "INACTIVE" | "LOCKED";
  verified: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
  roles: string[];
};

export type AdminRolePermission = {
  role: string;
  title: string;
  description: string;
  permissions: string[];
};

export type AdminAuditLog = {
  id: number;
  actor: string;
  action: string;
  target: string;
  ipAddress?: string | null;
  createdAt: string;
};

export type AdminAccountCreateRequest = {
  name: string;
  username?: string;
  email: string;
  password?: string;
  role: AdminAccount["role"] | "SUPER_ADMIN";
  status: AdminAccount["status"];
  verified?: boolean;
};
