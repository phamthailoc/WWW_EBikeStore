import { useEffect, useMemo, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { orderAPI } from "@ebike/shared-code/api";
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

const CustomerOrderDetailPage = () => {
  const { id } = useParams();
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadOrder = async () => {
      if (!id) {
        setError("Thiếu mã đơn hàng.");
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setOrder(await orderAPI.getDetail(id));
        setError(null);
      } catch (fetchError) {
        setError(fetchError instanceof Error ? fetchError.message : "Không thể tải chi tiết đơn hàng.");
      } finally {
        setLoading(false);
      }
    };

    void loadOrder();
  }, [id]);

  const itemTotal = useMemo(
    () => order?.items.reduce((sum, item) => sum + (item.lineTotal ?? item.unitPrice ?? 0), 0) ?? 0,
    [order]
  );

  return (
    <SectionShell
      eyebrow="Chi tiết đơn hàng"
      title="Theo dõi trạng thái giao hàng và thông tin đơn của bạn."
      description="Xem timeline vận chuyển, sản phẩm đã mua và thông tin nhận xe."
    >
      <div className="px-6 py-8">
        <Link to="/customer/orders" className="inline-flex items-center gap-2 text-sm font-semibold text-primary">
          <ArrowLeft className="h-4 w-4" />
          Quay lại danh sách đơn
        </Link>

        {loading ? (
          <div className="mt-8 rounded-lg bg-gray-50 p-6 text-center text-gray-600">Đang tải chi tiết đơn hàng...</div>
        ) : error || !order ? (
          <div className="mt-8 rounded-lg border border-red-200 bg-red-50 p-6 text-center text-red-700">
            {error || "Không tìm thấy đơn hàng."}
          </div>
        ) : (
          <div className="mt-8 grid gap-6 lg:grid-cols-[1.4fr,1fr]">
            <div className="space-y-6">
              <article className="rounded-xl border border-outline-variant/15 bg-white p-6 shadow-sm">
                <div className="flex flex-wrap items-center gap-3">
                  <h2 className="text-2xl font-bold">{order.orderNumber}</h2>
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
                {order.status === "CANCELLED" && order.cancellationReason && (
                  <p className="mt-3 text-sm text-rose-700">Lý do hủy: {order.cancellationReason}</p>
                )}
              </article>

              <article className="rounded-xl border border-outline-variant/15 bg-white p-6 shadow-sm">
                <h3 className="text-lg font-bold">Sản phẩm trong đơn</h3>
                <div className="mt-4 space-y-3">
                  {order.items.map((item) => (
                    <div key={`${order.id}-${item.id ?? item.productId}`} className="flex items-center justify-between gap-4 border-b border-slate-100 pb-3 last:border-0 last:pb-0">
                      <div>
                        <p className="font-medium">{item.productName || `Sản phẩm #${item.productId}`}</p>
                        <p className="text-sm text-muted-foreground">Số lượng: {item.quantity}</p>
                      </div>
                      <p className="font-semibold">{(item.lineTotal ?? item.unitPrice ?? 0).toLocaleString("vi-VN")}đ</p>
                    </div>
                  ))}
                </div>
                <div className="mt-5 border-t border-slate-100 pt-4 text-sm">
                  <div className="flex items-center justify-between text-muted-foreground">
                    <span>Tạm tính</span>
                    <span>{itemTotal.toLocaleString("vi-VN")}đ</span>
                  </div>
                  <div className="mt-2 flex items-center justify-between font-bold text-foreground">
                    <span>Tổng thanh toán</span>
                    <span>{order.totalAmount.toLocaleString("vi-VN")}đ</span>
                  </div>
                </div>
              </article>
            </div>

            <div className="space-y-6">
              <article className="rounded-xl border border-outline-variant/15 bg-white p-6 shadow-sm">
                <h3 className="text-lg font-bold">Timeline giao hàng</h3>
                <div className="mt-5">
                  <ShipmentTimeline order={order} />
                </div>
              </article>

              <article className="rounded-xl border border-outline-variant/15 bg-white p-6 shadow-sm">
                <h3 className="text-lg font-bold">Showroom nhận xe</h3>
                <p className="mt-3 font-semibold">{order.shipment?.pickupShowroom?.name || "Showroom đang cập nhật"}</p>
                <p className="mt-2 text-sm text-muted-foreground">{order.shipment?.pickupShowroom?.address || "-"}</p>
                <p className="mt-2 text-sm text-muted-foreground">{order.shipment?.pickupShowroom?.phone || ""}</p>
              </article>
            </div>
          </div>
        )}
      </div>
    </SectionShell>
  );
};

export default CustomerOrderDetailPage;
