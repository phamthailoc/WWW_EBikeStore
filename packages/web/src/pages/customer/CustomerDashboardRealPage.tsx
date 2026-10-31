import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { orderAPI } from "@ebike/shared-code/api";
import { useAppSelector } from "@ebike/shared-code/redux";
import type { Order } from "@ebike/shared-code/types";
import SectionShell from "../../components/common/SectionShell";

const CustomerDashboardRealPage = () => {
  const authUser = useAppSelector((state) => state.auth.user);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const latestRequestId = useRef(0);

  useEffect(() => {
    const requestId = latestRequestId.current + 1;
    latestRequestId.current = requestId;
    setOrders([]);
    setError("");

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
      } catch (loadError) {
        if (latestRequestId.current !== requestId) {
          return;
        }
        setError(loadError instanceof Error ? loadError.message : "Không thể tải tổng quan khách hàng.");
      } finally {
        if (latestRequestId.current === requestId) {
          setLoading(false);
        }
      }
    };

    void loadOrders();
  }, [authUser?.id]);

  const stats = useMemo(() => {
    const pendingOrders = orders.filter((order) => order.status === "PENDING").length;
    const totalSpent = orders.reduce((sum, order) => sum + order.totalAmount, 0);
    return { pendingOrders, totalSpent };
  }, [orders]);

  return (
    <SectionShell
      eyebrow="Bảng điều khiển khách hàng"
      title="Tổng quan nhanh về hoạt động gần đây của bạn."
      description="Theo dõi đơn hàng, chi tiêu và những bước tiếp theo trong hành trình mua xe điện."
    >
      <div className="space-y-6 px-6 py-8">
        {!authUser?.id ? (
          <div className="rounded-lg bg-gray-50 p-6 text-center text-gray-600">Bạn cần đăng nhập để xem dashboard.</div>
        ) : loading ? (
          <div className="rounded-lg bg-gray-50 p-6 text-center text-gray-600">Đang tải dashboard...</div>
        ) : error ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-red-700">{error}</div>
        ) : (
          <>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              <div className="rounded-xl border border-outline-variant/15 bg-white p-5">
                <p className="text-sm text-muted-foreground">Tổng số đơn</p>
                <p className="mt-2 text-3xl font-bold">{orders.length}</p>
              </div>
              <div className="rounded-xl border border-outline-variant/15 bg-white p-5">
                <p className="text-sm text-muted-foreground">Đơn chờ xử lý</p>
                <p className="mt-2 text-3xl font-bold">{stats.pendingOrders}</p>
              </div>
              <div className="rounded-xl border border-outline-variant/15 bg-white p-5">
                <p className="text-sm text-muted-foreground">Tổng chi tiêu</p>
                <p className="mt-2 text-3xl font-bold">{stats.totalSpent.toLocaleString("vi-VN")}đ</p>
              </div>
            </div>

            <section className="rounded-xl border border-outline-variant/15 bg-white p-6">
              <div className="mb-5 flex items-center justify-between gap-4">
                <h2 className="text-xl font-bold">Đơn hàng gần đây</h2>
                <Link to="/customer/orders" className="text-sm font-bold text-primary hover:underline">
                  Xem tất cả
                </Link>
              </div>
              {orders.length === 0 ? (
                <p className="text-muted-foreground">Bạn chưa có đơn hàng nào.</p>
              ) : (
                <div className="space-y-4">
                  {orders.slice(0, 3).map((order) => (
                    <div key={order.id} className="flex flex-col gap-2 rounded-lg bg-surface-container-low p-4 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <p className="font-bold">{order.orderNumber}</p>
                        <p className="text-sm text-muted-foreground">{new Date(order.createdAt).toLocaleString("vi-VN")}</p>
                      </div>
                      <div className="text-left sm:text-right">
                        <p className="font-bold text-primary">{order.totalAmount.toLocaleString("vi-VN")}đ</p>
                        <p className="text-sm text-muted-foreground">{order.status}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </>
        )}
      </div>
    </SectionShell>
  );
};

export default CustomerDashboardRealPage;
