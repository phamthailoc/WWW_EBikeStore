import { useEffect } from "react";
import { fetchOrdersThunk } from "../redux/slices/orderSlice";
import { useAppDispatch, useAppSelector } from "../redux";

export const useOrders = () => {
  const dispatch = useAppDispatch();
  const orders = useAppSelector((state) => state.orders);

  useEffect(() => {
    void dispatch(fetchOrdersThunk());
  }, [dispatch]);

  return orders;
};
