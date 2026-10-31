import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { authAPI } from "../../api";
import type { EnhancedAuthResponse, LoginRequest, SessionUser, UserProfileResponse } from "../../types";

interface AuthState {
  user: SessionUser | null;
  token: string | null;
  tokenExpiresAt: string | null;
  isLoading: boolean;
  isBootstrapping: boolean;
  isAuthenticated: boolean;
  error: string | null;
}

const mapAuthResponseToUser = (payload: EnhancedAuthResponse): SessionUser => ({
  id: String(payload.userId),
  username: payload.username,
  email: payload.email,
  fullName: payload.fullName,
  roles: payload.roles,
  active: payload.active,
  verified: payload.verified
});

const mapProfileResponseToUser = (payload: UserProfileResponse): SessionUser => ({
  id: String(payload.userId),
  username: payload.username,
  email: payload.email,
  fullName: [payload.firstName, payload.lastName].filter(Boolean).join(" ").trim() || payload.username,
  roles: payload.roles,
  active: payload.active,
  verified: payload.verified
});

const initialState: AuthState = {
  user: null,
  token: null,
  tokenExpiresAt: null,
  isLoading: false,
  isBootstrapping: true,
  isAuthenticated: false,
  error: null
};

export const loginThunk = createAsyncThunk<EnhancedAuthResponse, LoginRequest>(
  "auth/login",
  async (payload) => authAPI.login(payload)
);

export const bootstrapSessionThunk = createAsyncThunk<UserProfileResponse | null>(
  "auth/bootstrapSession",
  async () => authAPI.getSession()
);

export const logoutThunk = createAsyncThunk<void>(
  "auth/logout",
  async () => {
    await authAPI.logout();
  }
);

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(bootstrapSessionThunk.pending, (state) => {
        state.isBootstrapping = true;
      })
      .addCase(bootstrapSessionThunk.fulfilled, (state, action) => {
        state.user = action.payload ? mapProfileResponseToUser(action.payload) : null;
        state.isAuthenticated = Boolean(action.payload);
        state.isBootstrapping = false;
        state.error = null;
      })
      .addCase(bootstrapSessionThunk.rejected, (state) => {
        state.user = null;
        state.isAuthenticated = false;
        state.isBootstrapping = false;
      })
      .addCase(loginThunk.pending, (state) => {
        state.isLoading = true;
        state.user = null;
        state.isAuthenticated = false;
        state.error = null;
      })
      .addCase(loginThunk.fulfilled, (state, action) => {
        state.isLoading = false;
        state.user = mapAuthResponseToUser(action.payload);
        state.token = action.payload.token;
        state.tokenExpiresAt = action.payload.expiresAt;
        state.isAuthenticated = true;
        state.isBootstrapping = false;
      })
      .addCase(loginThunk.rejected, (state, action) => {
        state.isLoading = false;
        state.isAuthenticated = false;
        state.error = action.error.message ?? "Login failed";
      })
      .addCase(logoutThunk.pending, (state) => {
        state.user = null;
        state.token = null;
        state.tokenExpiresAt = null;
        state.isAuthenticated = false;
        state.error = null;
      })
      .addCase(logoutThunk.fulfilled, (state) => {
        state.user = null;
        state.token = null;
        state.tokenExpiresAt = null;
        state.isAuthenticated = false;
        state.error = null;
        state.isBootstrapping = false;
      })
      .addCase(logoutThunk.rejected, (state) => {
        state.user = null;
        state.token = null;
        state.tokenExpiresAt = null;
        state.isAuthenticated = false;
        state.error = null;
        state.isBootstrapping = false;
      });
  }
});

export default authSlice.reducer;
