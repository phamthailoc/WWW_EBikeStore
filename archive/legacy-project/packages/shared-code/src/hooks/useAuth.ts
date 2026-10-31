import { useAppDispatch, useAppSelector } from "../redux";
import { bootstrapSessionThunk, loginThunk, logoutThunk } from "../redux/slices/authSlice";
import type { LoginRequest } from "../types";

export const useAuth = () => {
  const dispatch = useAppDispatch();
  const auth = useAppSelector((state) => state.auth);

  return {
    ...auth,
    initializeSession: () => dispatch(bootstrapSessionThunk()),
    login: (payload: LoginRequest) => dispatch(loginThunk(payload)),
    logout: () => dispatch(logoutThunk())
  };
};
