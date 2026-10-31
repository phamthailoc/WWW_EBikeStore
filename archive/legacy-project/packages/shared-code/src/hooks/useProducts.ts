import { useEffect } from "react";
import { fetchProductsThunk } from "../redux/slices/productSlice";
import { useAppDispatch, useAppSelector } from "../redux";
import type { ProductFilter } from "../types";

export const useProducts = (filters?: ProductFilter) => {
  const dispatch = useAppDispatch();
  const products = useAppSelector((state) => state.products);

  useEffect(() => {
    void dispatch(fetchProductsThunk(filters));
  }, [dispatch, filters]);

  return products;
};
