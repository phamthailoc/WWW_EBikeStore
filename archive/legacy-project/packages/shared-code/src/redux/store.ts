import { configureStore } from "@reduxjs/toolkit";
import authReducer from "./slices/authSlice";
import productReducer from "./slices/productSlice";
import chatbotReducer from "./slices/chatbotSlice";
import orderReducer from "./slices/orderSlice";
import uiReducer from "./slices/uiSlice";
import { setupAuthInterceptor } from "../api/client";

export const createAppStore = () => {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      products: productReducer,
      chatbot: chatbotReducer,
      orders: orderReducer,
      ui: uiReducer
    }
  });

  // Setup auth interceptor to inject token into requests
  setupAuthInterceptor(() => {
    const state = store.getState();
    return state.auth.token;
  });

  return store;
};

export type AppStore = ReturnType<typeof createAppStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];
