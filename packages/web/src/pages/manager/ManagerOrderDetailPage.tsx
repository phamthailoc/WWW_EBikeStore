import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, CreditCard, MapPin, Package, Phone } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { managerAPI, orderAPI } from "@ebike/shared-code/api";
import type { Order } from "@ebike/shared-code/types";
import ShipmentTimeline from "../../components/orders/ShipmentTimeline";
import ManagerStatusBadge, { formatManagerLabel, orderTone, paymentTone } from "../../components/manager/ManagerStatusBadge";

const formatCurrency = (value: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(value);

const formatDateTime = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit"
      }).format(new Date(value))
    : "-";

const SHIPMENT_STATUS_OPTIONS = [
  { value: "PENDING", label: "Chờ xử lý" },
  { value: "PREPARING", label: "Đang chuẩn bị" },
  { value: "SHIPPED", label: "Đã giao cho vận chuyển" },
  { value: "IN_TRANSIT", label: "Đang giao" },
  { value: "DELIVERED", label: "Đã giao" },
  { value: "RETURNED", label: "Hoàn trả" }
];

const ManagerOrderDetailPage = () => {
  const { id } = useParams();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [shipmentStatus, setShipmentStatus] = useState("PENDING");
  const [trackingNumber, setTrackingNumber] = useState("");
  const [savingShipment, setSavingShipment] = useState(false);

  useEffect(() => {
    const loadOrder = async () => {
      if (!id) {
        setError("Thiếu mã đơn hàng.");
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const loadedOrder = await orderAPI.getDetail(id);
        setOrder(loadedOrder);
        setShipmentStatus(loadedOrder.shipment?.shipmentStatus || "PENDING");
        setTrackingNumber(loadedOrder.shipment?.trackingNumber || "");
        setError("");
      } catch (orderError) {
        setError(orderError instanceof Error ? orderError.message : "Không thể tải chi tiết đơn hàng.");
      } finally {
        setLoading(false);
      }
    };

    void loadOrder();
  }, [id]);

  const itemTotal = useMemo(() => order?.items.reduce((sum, item) => sum + (item.lineTotal ?? 0), 0) ?? 0, [order]);

  const saveShipmentUpdate = async () => {
    if (!order) {
      return;
    }

    try {
      setSavingShipment(true);
      setError("");
      const updatedOrder = await managerAPI.updateOrderShipment(order.id, {
        shipmentStatus,
        trackingNumber: trackingNumber.trim() || undefined
      });
      setOrder(updatedOrder);
      setShipmentStatus(updatedOrder.shipment?.shipmentStatus || shipmentStatus);
      setTrackingNumber(updatedOrder.shipment?.trackingNumber || "");
    } catch (updateError) {
      setError(updateError instanceof Error ? updateError.message : "Không thể cập nhật trạng thái giao hàng.");
    } finally {
      setSavingShipment(false);
    }
  };

  if (loading) {
    return <div className="rounded-lg border border-slate-200 bg-white px-6 py-10 text-sm text-slate-500">Đang tải chi tiết đơn hàng...</div>;
  }

  if (error && !order) {
    return <div className="rounded-lg border border-red-200 bg-red-50 px-6 py-10 text-sm text-red-700">{error}</div>;
  }

  if (!order) {
    return <div className="rounded-lg border border-red-200 bg-red-50 px-6 py-10 text-sm text-red-700">Không tìm thấy đơn hàng.</div>;
  }

  return (
    <div className="space-y-6">
      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <Link to="/manager/orders" className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 transition hover:text-slate-900">
            <ArrowLeft className="h-4 w-4" />
            <span>Quay lại danh sách đơn</span>
          </Link>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <h2 className="text-3xl font-bold text-slate-950">{order.orderNumber}</h2>
            <ManagerStatusBadge label={order.status} tone={orderTone(order.status)} />
          </div>
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.6fr,1fr]">
        <div className="space-y-6">
          <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-200 px-6 py-5">
              <h3 className="text-lg font-bold text-slate-950">Sản phẩm đã mua</h3>
            </div>
            <div className="divide-y divide-slate-100">
              {order.items.map((item) => (
                <div key={item.id ?? item.productId} className="flex items-start gap-4 px-6 py-5">
                  <div className="rounded-lg bg-slate-100 p-3 text-slate-500">
                    <Package className="h-5 w-5" />
                  </div>
                  <div className="flex-1">
                    <p className="font-bold text-slate-950">{item.productName || `Sản phẩm #${item.productId}`}</p>
                    <p className="mt-1 text-sm text-slate-500">Số lượng: {item.quantity}</p>
                  </div>
                  <p className="text-sm font-bold text-slate-950">{formatCurrency(item.lineTotal ?? item.unitPrice ?? 0)}</p>
                </div>
              ))}
            </div>
            <div className="space-y-3 border-t border-slate-200 bg-slate-50 px-6 py-5 text-sm">
              <div className="flex items-center justify-between text-slate-600">
                <span>Tạm tính</span>
                <span>{formatCurrency(itemTotal || order.subtotal)}</span>
              </div>
              <div className="flex items-center justify-between border-t border-slate-200 pt-3 text-base font-bold text-slate-950">
                <span>Tổng cộng</span>
                <span>{formatCurrency(order.totalAmount)}</span>
              </div>
            </div>
          </section>

          {(order.status === "CANCELLED" || order.cancellationReason) && (
            <section className="rounded-lg border border-rose-200 bg-white p-6 shadow-sm">
              <h3 className="text-lg font-bold text-slate-950">Thông tin hủy đơn</h3>
              <div className="mt-4 space-y-3 text-sm leading-7 text-slate-600">
                <p>
                  <span className="font-semibold text-slate-900">Lý do khách hàng:</span>{" "}
                  {order.cancellationReason || "Khách hàng không nhập lý do."}
                </p>
                <p>
                  <span className="font-semibold text-slate-900">Thời điểm hủy:</span>{" "}
                  {formatDateTime(order.cancellationRequestedAt)}
                </p>
              </div>
            </section>
          )}
        </div>

        <div className="space-y-6">
          <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
            <h3 className="text-lg font-bold text-slate-950">Thông tin khách hàng</h3>
            <div className="mt-5 space-y-4 text-sm text-slate-600">
              <div>
                <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Tên khách</p>
                <p className="mt-2 font-semibold text-slate-950">{order.shipment?.recipientName || "Đơn khách vãng lai"}</p>
              </div>
              <div className="flex items-start gap-3">
                <Phone className="mt-0.5 h-4 w-4 text-slate-400" />
                <span>{order.shipment?.phoneNumber || "-"}</span>
              </div>
              <div className="flex items-start gap-3">
                <CreditCard className="mt-0.5 h-4 w-4 text-slate-400" />
                <span>{order.customerEmail || "-"}</span>
              </div>
              <div className="flex items-start gap-3">
                <MapPin className="mt-0.5 h-4 w-4 text-slate-400" />
                <span>{order.shipment?.detailedAddress || "-"}</span>
              </div>
            </div>
          </section>

          <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
            <h3 className="text-lg font-bold text-slate-950">Timeline giao hàng</h3>
            <div className="mt-5">
              <ShipmentTimeline order={order} compact />
            </div>
          </section>

          {order.status !== "CANCELLED" && order.shipment && (
            <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
              <h3 className="text-lg font-bold text-slate-950">Cập nhật trạng thái giao hàng</h3>
              <div className="mt-5 space-y-4">
                <label className="block">
                  <span className="text-sm font-semibold text-slate-700">Trạng thái</span>
                  <select
                    value={shipmentStatus}
                    onChange={(event) => setShipmentStatus(event.target.value)}
                    className="mt-2 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm outline-none transition focus:border-[#003b93] focus:bg-white"
                  >
                    {SHIPMENT_STATUS_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="block">
                  <span className="text-sm font-semibold text-slate-700">Mã vận đơn</span>
                  <input
                    value={trackingNumber}
                    onChange={(event) => setTrackingNumber(event.target.value)}
                    placeholder="Nhập mã vận đơn nếu có"
                    className="mt-2 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm outline-none transition focus:border-[#003b93] focus:bg-white"
                  />
                </label>
                <button
                  type="button"
                  onClick={() => void saveShipmentUpdate()}
                  disabled={savingShipment}
                  className="w-full rounded-lg bg-[#003b93] px-4 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {savingShipment ? "Đang lưu..." : "Lưu trạng thái giao hàng"}
                </button>
              </div>
            </section>
          )}

          <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
            <h3 className="text-lg font-bold text-slate-950">Thanh toán</h3>
            <div className="mt-5 space-y-4 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-slate-500">Phương thức</span>
                <span className="font-semibold text-slate-950">{formatManagerLabel(order.paymentMethod)}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-slate-500">Trạng thái</span>
                <ManagerStatusBadge label={order.paymentStatus} tone={paymentTone(order.paymentStatus)} />
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
};

export default ManagerOrderDetailPage;
