import type { Order } from "@ebike/shared-code/types";
import { CheckCircle2 } from "lucide-react";
import { useEffect } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";

interface OrderSuccessLocationState {
  order?: Order;
}

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: "Chưa thanh toán",
  PAID: "Đã thanh toán",
  FAILED: "Thanh toán thất bại",
  CANCELLED: "Đã hủy",
  REFUNDED: "Đã hoàn tiền"
};

const OrderSuccessPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const state = (location.state as OrderSuccessLocationState | undefined) ?? {};
  const order = state.order;

  useEffect(() => {
    if (!order) {
      navigate("/products", { replace: true });
    }
  }, [navigate, order]);

  if (!order) {
    return null;
  }

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col items-center justify-center px-6 py-32 text-center">
      <CheckCircle2 className="mb-6 h-16 w-16 text-emerald-600" />
      <h1 className="text-4xl font-bold tracking-tight">Đặt hàng thành công</h1>
      <p className="mt-4 max-w-xl text-muted-foreground">
        Đơn hàng của bạn đã được ghi nhận. Showroom sẽ liên hệ để xác nhận và hướng dẫn thanh toán sau.
      </p>

      <div className="mt-8 w-full rounded-lg border border-outline-variant/20 bg-white p-6 text-left text-sm">
        <div className="flex justify-between gap-4 py-2">
          <span className="text-muted-foreground">Mã đơn</span>
          <span className="font-medium">{order.orderNumber}</span>
        </div>
        <div className="flex justify-between gap-4 py-2">
          <span className="text-muted-foreground">Tổng thanh toán</span>
          <span className="font-medium">{order.totalAmount.toLocaleString("vi-VN")}đ</span>
        </div>
        <div className="flex justify-between gap-4 py-2">
          <span className="text-muted-foreground">Trạng thái đơn</span>
          <span className="font-medium">{order.status}</span>
        </div>
        <div className="flex justify-between gap-4 py-2">
          <span className="text-muted-foreground">Trạng thái thanh toán</span>
          <span className="font-medium">{PAYMENT_STATUS_LABELS[order.paymentStatus || ""] || order.paymentStatus || "-"}</span>
        </div>
      </div>

      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <Link to="/customer/orders" className="rounded-lg bg-primary px-5 py-3 text-sm font-bold text-white">
          Xem đơn hàng
        </Link>
        <Link to="/products" className="rounded-lg border border-outline-variant/30 px-5 py-3 text-sm font-bold">
          Tiếp tục mua sắm
        </Link>
      </div>
    </div>
  );
};

export default OrderSuccessPage;
