import { createAsyncThunk, createSlice } from "@reduxjs/toolkit";
import { productAPI } from "../../api";
import type { Product, ProductFilter } from "../../types";

interface ProductState {
  items: Product[];
  loading: boolean;
  error: string | null;
  filters: ProductFilter;
}

const initialState: ProductState = {
  items: [],
  loading: false,
  error: null,
  filters: {}
};

export const fetchProductsThunk = createAsyncThunk<Product[], ProductFilter | undefined>(
  "products/fetchProducts",
  async (filters) => productAPI.list(filters)
);

const productSlice = createSlice({
  name: "products",
  initialState,
  reducers: {
    setFilters: (state, action) => {
      state.filters = action.payload;
    }
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchProductsThunk.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchProductsThunk.fulfilled, (state, action) => {
        state.loading = false;
        state.items = action.payload;
      })
      .addCase(fetchProductsThunk.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message ?? "Unable to load products";
      });
  }
});

export const { setFilters } = productSlice.actions;
export default productSlice.reducer;
