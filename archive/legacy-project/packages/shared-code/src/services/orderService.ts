import type { Order } from "../types";

export const orderService = {
  validateOrder: (order: Order) => order.items.length > 0 && order.totalAmount >= 0
};
