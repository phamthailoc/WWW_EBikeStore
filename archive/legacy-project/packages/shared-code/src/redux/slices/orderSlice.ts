import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { orderAPI } from "../../api";
import type { Order } from "../../types";

interface OrderState {
  orders: Order[];
  currentOrder: Order | null;
}

const initialState: OrderState = {
  orders: [],
  currentOrder: null
};

export const fetchOrdersThunk = createAsyncThunk("orders/fetchOrders", async () => orderAPI.list());

const orderSlice = createSlice({
  name: "orders",
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(fetchOrdersThunk.fulfilled, (state, action) => {
      state.orders = action.payload;
    });
  }
});

export default orderSlice.reducer;
