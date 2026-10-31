import React from "react";
import ReactDOM from "react-dom/client";
import { Provider } from "react-redux";
import { RouterProvider } from "react-router-dom";
import { createAppStore } from "@ebike/shared-code/redux";
import AuthBootstrap from "./components/common/AuthBootstrap";
import router from "./router";
import "./styles/globals.css";

const store = createAppStore();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <Provider store={store}>
      <AuthBootstrap />
      <RouterProvider router={router} />
    </Provider>
  </React.StrictMode>
);
