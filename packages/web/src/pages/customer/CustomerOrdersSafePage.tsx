import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { orderAPI } from "@ebike/shared-code/api";
import { useAppSelector } from "@ebike/shared-code/redux";
import type { Order } from "@ebike/shared-code/types";
import SectionShell from "../../components/common/SectionShell";
import ShipmentTimeline from "../../components/orders/ShipmentTimeline";

const STATUS_LABELS: Record<string, string> = {
  PENDING: "Chờ xác nhận",
  CONFIRMED: "Đã xác nhận",
  PROCESSING: "Đang xử lý",
  SHIPPED: "Đang giao",
  DELIVERED: "Đã giao",
  CANCELLED: "Đã hủy"
};

const STATUS_CLASSES: Record<string, string> = {
  PENDING: "bg-amber-50 text-amber-700 border-amber-200",
  CONFIRMED: "bg-blue-50 text-blue-700 border-blue-200",
  PROCESSING: "bg-indigo-50 text-indigo-700 border-indigo-200",
  SHIPPED: "bg-violet-50 text-violet-700 border-violet-200",
  DELIVERED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  CANCELLED: "bg-rose-50 text-rose-700 border-rose-200"
};

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: "Chưa thanh toán",
  PAID: "Đã thanh toán",
  FAILED: "Thanh toán thất bại",
  CANCELLED: "Đã hủy",
  REFUNDED: "Đã hoàn tiền"
};

const CustomerOrdersSafePage = () => {
  const authUser = useAppSelector((state) => state.auth.user);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [cancellationError, setCancellationError] = useState<string | null>(null);
  const [cancellingOrderId, setCancellingOrderId] = useState<number | null>(null);
  const [cancellationOrder, setCancellationOrder] = useState<Order | null>(null);
  const [cancellationReason, setCancellationReason] = useState("");
  const [cancellationValidationError, setCancellationValidationError] = useState("");
  const latestRequestId = useRef(0);

  useEffect(() => {
    const requestId = latestRequestId.current + 1;
    latestRequestId.current = requestId;
    setOrders([]);
    setError(null);

    const loadOrders = async () => {
      if (!authUser?.id) {
        setLoading(false);
        return;
      }

      setLoading(true);
      try {
        const data = await orderAPI.list();
        if (latestRequestId.current !== requestId) {
          return;
        }
        setOrders(data);
      } catch (fetchError) {
        if (latestRequestId.current !== requestId) {
          return;
        }
        setError(fetchError instanceof Error ? fetchError.message : "Không thể tải danh sách đơn hàng.");
        setOrders([]);
      } finally {
        if (latestRequestId.current === requestId) {
          setLoading(false);
        }
      }
    };

    void loadOrders();
  }, [authUser?.id]);

  const totalSpent = useMemo(() => orders.reduce((sum, order) => sum + order.totalAmount, 0), [orders]);

  const canCancelOrder = (order: Order) => {
    if (order.status === "CANCELLED" || order.status === "DELIVERED" || order.status === "SHIPPED") {
      return false;
    }
    const shipmentStatus = order.shipment?.shipmentStatus;
    if (shipmentStatus === "SHIPPED" || shipmentStatus === "IN_TRANSIT" || shipmentStatus === "DELIVERED") {
      return false;
    }
    return order.status === "PENDING" || order.status === "CONFIRMED" || order.status === "PROCESSING";
  };

  const openCancellationDialog = (order: Order) => {
    setCancellationOrder(order);
    setCancellationReason("");
    setCancellationValidationError("");
    setCancellationError(null);
  };

  const closeCancellationDialog = () => {
    if (cancellingOrderId !== null) {
      return;
    }
    setCancellationOrder(null);
    setCancellationReason("");
    setCancellationValidationError("");
  };

  const submitCancellationRequest = async () => {
    if (!cancellationOrder) {
      return;
    }

    const reason = cancellationReason.trim();
    if (!reason) {
      setCancellationValidationError("Vui lòng nhập lý do hủy đơn.");
      return;
    }
    if (reason.length > 1000) {
      setCancellationValidationError("Lý do hủy không được vượt quá 1000 ký tự.");
      return;
    }

    setCancellationError(null);
    setCancellationValidationError("");
    setCancellingOrderId(cancellationOrder.id);
    try {
      const updatedOrder = await orderAPI.requestCancellation(cancellationOrder.id, { reason });
      setOrders((currentOrders) =>
        currentOrders.map((currentOrder) => (currentOrder.id === updatedOrder.id ? updatedOrder : currentOrder))
      );
      setCancellationOrder(null);
      setCancellationReason("");
    } catch (cancelError) {
      setCancellationError(cancelError instanceof Error ? cancelError.message : "Không thể hủy đơn hàng.");
    } finally {
      setCancellingOrderId(null);
    }
  };

  return (
    <SectionShell
      eyebrow="Đơn hàng của tôi"
      title="Theo dõi những lần mua xe điện hiện tại và trước đây."
      description="Danh sách này hiển thị các đơn bạn đã tạo, trạng thái xử lý và địa điểm nhận xe tương ứng."
    >
      <div className="px-6 py-8">
        {cancellationError && (
          <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {cancellationError}
          </div>
        )}
        {!authUser?.id ? (
          <div className="rounded-lg bg-gray-50 p-6 text-center text-gray-600">
            Bạn cần đăng nhập để xem lịch sử đơn hàng.
          </div>
        ) : loading ? (
          <div className="rounded-lg bg-gray-50 p-6 text-center text-gray-600">
            Đang tải danh sách đơn hàng...
          </div>
        ) : error ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-red-700">
            {error}
          </div>
        ) : orders.length === 0 ? (
          <div className="rounded-lg bg-gray-50 p-6 text-center text-gray-600">
            Bạn chưa có đơn hàng nào.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <div className="rounded-xl border border-outline-variant/15 bg-white p-5">
                <p className="text-sm text-muted-foreground">Tổng số đơn</p>
                <p className="mt-2 text-3xl font-bold">{orders.length}</p>
              </div>
              <div className="rounded-xl border border-outline-variant/15 bg-white p-5">
                <p className="text-sm text-muted-foreground">Đơn chờ xử lý</p>
                <p className="mt-2 text-3xl font-bold">{orders.filter((order) => order.status === "PENDING").length}</p>
              </div>
              <div className="rounded-xl border border-outline-variant/15 bg-white p-5">
                <p className="text-sm text-muted-foreground">Tổng chi tiêu</p>
                <p className="mt-2 text-3xl font-bold">{totalSpent.toLocaleString("vi-VN")}đ</p>
              </div>
            </div>

            <div className="space-y-4">
              {orders.map((order) => (
                <article key={order.id} className="rounded-xl border border-outline-variant/15 bg-white p-6 shadow-sm">
                  <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                    <div>
                      <div className="flex flex-wrap items-center gap-3">
                        <h2 className="text-2xl font-bold">
                          <Link to={`/customer/orders/${order.id}`} className="transition hover:text-primary">
                            {order.orderNumber}
                          </Link>
                        </h2>
                        <span
                          className={`rounded-full border px-3 py-1 text-xs font-semibold ${
                            STATUS_CLASSES[order.status] || "border-gray-200 bg-gray-50 text-gray-700"
                          }`}
                        >
                          {STATUS_LABELS[order.status] || order.status}
                        </span>
                      </div>
                      <p className="mt-2 text-sm text-muted-foreground">
                        Tạo lúc {new Date(order.createdAt).toLocaleString("vi-VN")}
                      </p>
                      <p className="mt-1 text-sm text-muted-foreground">
                        Nhận xe tại {order.shipment?.pickupShowroom?.name || "Showroom đang cập nhật"}
                      </p>
                      <p className="mt-1 text-sm text-muted-foreground">
                        Dịch vụ đăng ký xe:{" "}
                        <span className="font-semibold text-foreground">
                          {order.includeRegistrationService ? "Nhờ showroom làm giấy tờ xe" : "Khách tự làm giấy tờ xe"}
                        </span>
                      </p>
                      <p className="mt-1 text-sm font-semibold text-muted-foreground">
                        Thanh toán: {PAYMENT_STATUS_LABELS[order.paymentStatus || ""] || "Chưa khởi tạo"}
                      </p>
                      {order.status === "CANCELLED" && order.cancellationReason && (
                        <p className="mt-2 text-sm text-rose-700">Lý do hủy: {order.cancellationReason}</p>
                      )}
                    </div>
                    <div className="text-left md:text-right">
                      <p className="text-sm text-muted-foreground">Tổng thanh toán</p>
                      <p className="mt-2 text-2xl font-bold text-primary">{order.totalAmount.toLocaleString("vi-VN")}đ</p>
                      {canCancelOrder(order) && (
                        <button
                          type="button"
                          onClick={() => openCancellationDialog(order)}
                          disabled={cancellingOrderId === order.id}
                          className="mt-4 rounded-lg border border-rose-200 px-4 py-2 text-sm font-semibold text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {cancellingOrderId === order.id ? "Đang hủy..." : "Hủy đơn"}
                        </button>
                      )}
                    </div>
                  </div>

                  <div className="mt-5 border-t border-outline-variant/10 pt-5">
                    <div className="mb-4 flex items-center justify-between gap-3">
                      <p className="text-sm font-semibold text-foreground">Trạng thái giao hàng</p>
                      <Link to={`/customer/orders/${order.id}`} className="text-sm font-semibold text-primary">
                        Xem chi tiết
                      </Link>
                    </div>
                    <ShipmentTimeline order={order} compact />
                  </div>

                  <div className="mt-5 border-t border-outline-variant/10 pt-5">
                    <p className="mb-3 text-sm font-semibold text-foreground">Sản phẩm trong đơn</p>
                    <div className="space-y-3">
                      {order.items.map((item) => (
                        <div key={`${order.id}-${item.id ?? item.productId}`} className="flex items-center justify-between gap-4">
                          <div>
                            <p className="font-medium">{item.productName || `Sản phẩm #${item.productId}`}</p>
                            <p className="text-sm text-muted-foreground">Số lượng: {item.quantity}</p>
                          </div>
                          <p className="font-semibold">{(item.lineTotal ?? item.unitPrice ?? 0).toLocaleString("vi-VN")}đ</p>
                        </div>
                      ))}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </div>
        )}

        {cancellationOrder && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 px-4 py-6">
            <div className="w-full max-w-lg rounded-lg bg-white shadow-2xl">
              <div className="border-b border-slate-200 px-6 py-5">
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-rose-600">Hủy đơn hàng</p>
                <h3 className="mt-2 text-xl font-bold text-slate-950">{cancellationOrder.orderNumber}</h3>
                <p className="mt-2 text-sm text-slate-500">
                  Đơn sẽ được hủy ngay sau khi bạn nhập lý do và xác nhận.
                </p>
              </div>

              <div className="space-y-4 px-6 py-5">
                <label className="block">
                  <span className="text-sm font-semibold text-slate-700">Lý do hủy (bắt buộc)</span>
                  <textarea
                    value={cancellationReason}
                    onChange={(event) => setCancellationReason(event.target.value)}
                    rows={5}
                    maxLength={1000}
                    placeholder="Ví dụ: Tôi muốn thay đổi mẫu xe hoặc chưa sắp xếp được thời gian nhận xe."
                    className="mt-2 w-full resize-none rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition focus:border-rose-400 focus:bg-white focus:ring-4 focus:ring-rose-100"
                  />
                </label>
                <div className="flex items-center justify-between gap-3 text-xs">
                  <span className="text-rose-600">{cancellationValidationError}</span>
                  <span className="text-slate-400">{cancellationReason.length}/1000</span>
                </div>
              </div>

              <div className="flex flex-col-reverse gap-3 border-t border-slate-200 px-6 py-4 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={closeCancellationDialog}
                  disabled={cancellingOrderId === cancellationOrder.id}
                  className="rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Hủy
                </button>
                <button
                  type="button"
                  onClick={() => void submitCancellationRequest()}
                  disabled={cancellingOrderId === cancellationOrder.id}
                  className="rounded-lg bg-rose-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {cancellingOrderId === cancellationOrder.id ? "Đang hủy..." : "Xác nhận hủy"}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </SectionShell>
  );
};

export default CustomerOrdersSafePage;
