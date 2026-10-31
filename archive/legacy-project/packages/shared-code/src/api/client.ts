import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";
import { API_BASE_URL } from "../config/api.config";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true
});

type ApiErrorBody = {
  message?: string;
  detail?: string;
  error?: string;
  status?: number;
};

type ClientRateLimitRule = {
  name: string;
  method: string;
  path: string;
  maxRequests: number;
  windowMs: number;
};

type ClientRateLimitState = {
  timestamps: number[];
};

type RetryableRequestConfig = InternalAxiosRequestConfig & {
  _retried?: boolean;
};

class ClientRateLimitError extends Error {
  retryAfterSeconds: number;

  constructor(retryAfterSeconds: number) {
    super(`Thao tác quá nhanh. Vui lòng thử lại sau ${retryAfterSeconds} giây.`);
    this.name = "ClientRateLimitError";
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

const clientRateLimitRules: ClientRateLimitRule[] = [
  { name: "auth-login", method: "POST", path: "/auth/login", maxRequests: 1, windowMs: 3000 },
  { name: "auth-enhanced-login", method: "POST", path: "/auth/login/enhanced", maxRequests: 1, windowMs: 3000 },
  { name: "email-otp-send", method: "POST", path: "/orders/email-verification/send", maxRequests: 1, windowMs: 30000 },
  { name: "chatbot-ask", method: "POST", path: "/chatbot/ask", maxRequests: 1, windowMs: 3000 },
  { name: "vnpay-create", method: "POST", path: "/payments/vnpay/create", maxRequests: 1, windowMs: 5000 }
];

const clientRateLimitState = new Map<string, ClientRateLimitState>();

const extractApiErrorMessage = (error: AxiosError<ApiErrorBody>) => {
  const body = error.response?.data;
  if (body?.message) {
    return body.message;
  }
  if (body?.detail) {
    return body.detail;
  }
  if (body?.error) {
    return body.error;
  }
  return error.message;
};

const normalizeRequestPath = (url?: string) => {
  if (!url) {
    return "";
  }

  try {
    const parsedUrl = new URL(url, API_BASE_URL);
    return parsedUrl.pathname.replace(/^\/api\/v1/, "");
  } catch {
    return url.split("?")[0].replace(/^\/api\/v1/, "");
  }
};

const findClientRateLimitRule = (config: InternalAxiosRequestConfig) => {
  const method = (config.method || "GET").toUpperCase();
  const path = normalizeRequestPath(config.url);

  return clientRateLimitRules.find((rule) => rule.method === method && rule.path === path);
};

const enforceClientRateLimit = (config: InternalAxiosRequestConfig) => {
  const rule = findClientRateLimitRule(config);
  if (!rule) {
    return;
  }

  const now = Date.now();
  const state = clientRateLimitState.get(rule.name) ?? { timestamps: [] };
  state.timestamps = state.timestamps.filter((timestamp) => now - timestamp < rule.windowMs);

  if (state.timestamps.length >= rule.maxRequests) {
    const oldestTimestamp = state.timestamps[0] ?? now;
    const retryAfterMs = Math.max(0, rule.windowMs - (now - oldestTimestamp));
    throw new ClientRateLimitError(Math.max(1, Math.ceil(retryAfterMs / 1000)));
  }

  state.timestamps.push(now);
  clientRateLimitState.set(rule.name, state);
};

const shouldRetryRequest = (error: AxiosError<ApiErrorBody>) => {
  const config = error.config as RetryableRequestConfig | undefined;
  const method = (config?.method || "GET").toUpperCase();
  const retryableError = error.code === "ECONNABORTED" || error.message.includes("timeout") || !error.response;

  return Boolean(config && method === "GET" && retryableError && !config._retried);
};

const retryRequest = async (error: AxiosError<ApiErrorBody>) => {
  const config = error.config as RetryableRequestConfig;
  config._retried = true;
  await new Promise((resolve) => setTimeout(resolve, 500));
  return apiClient.request(config);
};

// Store reference to the token getter function - will be set by setupAuthInterceptor
let getAuthToken: (() => string | null) | null = null;

/**
 * Setup auth interceptor with access to Redux store token.
 * Call this once after store is created.
 */
export const setupAuthInterceptor = (tokenGetter: () => string | null) => {
  getAuthToken = tokenGetter;
};

// Request interceptor: Add token to Authorization header
apiClient.interceptors.request.use(
  (config) => {
    enforceClientRateLimit(config);

    if (getAuthToken) {
      const token = getAuthToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Handle errors
apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError<ApiErrorBody>(error)) {
      return Promise.reject(error);
    }

    if (shouldRetryRequest(error)) {
      return retryRequest(error);
    }

    // Handle 401 Unauthorized - could implement token refresh here if needed
    if (error.response?.status === 401) {
      // Token expired or invalid - could trigger logout here
      // For now, just pass the error through to be handled by the app
    }
    return Promise.reject(new Error(extractApiErrorMessage(error)));
  }
);
