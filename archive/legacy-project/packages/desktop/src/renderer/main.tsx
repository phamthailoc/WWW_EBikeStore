import React from "react";
import ReactDOM from "react-dom/client";
import { Provider } from "react-redux";
import { createAppStore } from "@ebike/shared-code/redux";
import App from "./App";

const store = createAppStore();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <Provider store={store}>
      <App />
    </Provider>
  </React.StrictMode>
);
