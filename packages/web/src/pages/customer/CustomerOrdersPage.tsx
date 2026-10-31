import { useEffect, useMemo, useState } from "react";
import { orderAPI } from "@ebike/shared-code/api";
import { useAppSelector } from "@ebike/shared-code/redux";
import type { Order } from "@ebike/shared-code/types";
import SectionShell from "../../components/common/SectionShell";

const STATUS_LABELS: Record<string, string> = {
  PENDING: "Chờ xác nhận",
  CONFIRMED: "Đã xác nhận",
  SHIPPED: "Đang giao",
  DELIVERED: "Đã giao",
  CANCELLED: "Đã hủy"
};

const STATUS_CLASSES: Record<string, string> = {
  PENDING: "bg-amber-50 text-amber-700 border-amber-200",
  CONFIRMED: "bg-blue-50 text-blue-700 border-blue-200",
  SHIPPED: "bg-violet-50 text-violet-700 border-violet-200",
  DELIVERED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  CANCELLED: "bg-rose-50 text-rose-700 border-rose-200"
};

const CustomerOrdersPage = () => {
  const authUser = useAppSelector((state) => state.auth.user);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadOrders = async () => {
      if (!authUser?.id) {
        setOrders([]);
        setLoading(false);
        return;
      }

      setLoading(true);
      try {
        const data = await orderAPI.list();
        setOrders(data);
        setError(null);
      } catch (fetchError) {
        setError(fetchError instanceof Error ? fetchError.message : "Không thể tải danh sách đơn hàng.");
        setOrders([]);
      } finally {
        setLoading(false);
      }
    };

    void loadOrders();
  }, [authUser?.id]);

  const totalSpent = useMemo(
    () => orders.reduce((sum, order) => sum + order.totalAmount, 0),
    [orders]
  );

  return (
    <SectionShell
      eyebrow="Đơn hàng của tôi"
      title="Theo dõi những lần mua xe điện hiện tại và trước đây."
      description="Danh sách này hiển thị các đơn bạn đã tạo, trạng thái xử lý và địa điểm nhận xe tương ứng."
    >
      <div className="px-6 py-8">
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
                      <p className="mt-1 text-sm text-muted-foreground">
                        Nhận xe tại {order.shipment?.pickupShowroom?.name || "Showroom đang cập nhật"}
                      </p>
                      <p className="mt-1 text-sm text-muted-foreground">
                        Dịch vụ đăng ký xe:{" "}
                        <span className="font-semibold text-foreground">
                          {order.includeRegistrationService ? "Nhờ showroom làm giấy tờ xe" : "Khách tự làm giấy tờ xe"}
                        </span>
                      </p>
                    </div>
                    <div className="text-left md:text-right">
                      <p className="text-sm text-muted-foreground">Tổng thanh toán</p>
                      <p className="mt-2 text-2xl font-bold text-primary">{order.totalAmount.toLocaleString("vi-VN")}đ</p>
                    </div>
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
      </div>
    </SectionShell>
  );
};

export default CustomerOrdersPage;
