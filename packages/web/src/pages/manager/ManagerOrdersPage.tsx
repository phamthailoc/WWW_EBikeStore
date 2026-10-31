import { useEffect, useMemo, useState } from "react";
import { Eye, RotateCcw } from "lucide-react";
import { Link } from "react-router-dom";
import { orderAPI } from "@ebike/shared-code/api";
import type { Order } from "@ebike/shared-code/types";
import ManagerStatusBadge, { formatManagerLabel, orderTone, paymentTone } from "../../components/manager/ManagerStatusBadge";

const formatCurrency = (value: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(value);

const formatDate = (value: string) =>
  new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(value));

const ManagerOrdersPage = () => {
  const [orders, setOrders] = useState<Order[]>([]);
  const [status, setStatus] = useState("");
  const [paymentStatus, setPaymentStatus] = useState("");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadOrders = async (params?: { status?: string; paymentStatus?: string; search?: string }) => {
    try {
      setLoading(true);
      const data = await orderAPI.list(params);
      setOrders(data);
      setError("");
    } catch (ordersError) {
      setError(ordersError instanceof Error ? ordersError.message : "Không thể tải danh sách đơn hàng.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadOrders();
  }, []);

  const applyFilters = async () => {
    await loadOrders({
      status: status || undefined,
      paymentStatus: paymentStatus || undefined,
      search: search.trim() || undefined
    });
  };

  const resetFilters = async () => {
    setStatus("");
    setPaymentStatus("");
    setSearch("");
    await loadOrders();
  };

  const metrics = useMemo(
    () => ({
      total: orders.length,
      pending: orders.filter((order) => order.status === "PENDING").length,
      cancellationRequested: orders.filter((order) => order.status === "CANCELLATION_REQUESTED").length,
      inTransit: orders.filter((order) => order.status === "SHIPPED" || order.status === "PROCESSING").length,
      completed: orders.filter((order) => order.status === "DELIVERED").length
    }),
    [orders]
  );

  return (
    <div className="space-y-6">
      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {[
          { label: "Tổng đơn hàng", value: metrics.total },
          { label: "Chờ xác nhận", value: metrics.pending },
          { label: "Chờ duyệt hủy", value: metrics.cancellationRequested },
          { label: "Đang vận chuyển", value: metrics.inTransit },
          { label: "Hoàn tất", value: metrics.completed }
        ].map((metric) => (
          <article key={metric.label} className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">{metric.label}</p>
            <p className="mt-3 text-2xl font-bold text-slate-950">{metric.value}</p>
          </article>
        ))}
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="grid gap-4 xl:grid-cols-[1fr,1fr,1.4fr,auto]">
          <label className="space-y-2">
            <span className="block text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Trạng thái đơn</span>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none transition focus:border-[#003b93] focus:bg-white"
            >
              <option value="">Tất cả trạng thái</option>
              <option value="PENDING">Chờ xử lý</option>
              <option value="CONFIRMED">Đã xác nhận</option>
              <option value="CANCELLATION_REQUESTED">Chờ duyệt hủy</option>
              <option value="PROCESSING">Đang xử lý</option>
              <option value="SHIPPED">Đang giao</option>
              <option value="DELIVERED">Đã giao</option>
              <option value="CANCELLED">Đã hủy</option>
            </select>
          </label>

          <label className="space-y-2">
            <span className="block text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Trạng thái thanh toán</span>
            <select
              value={paymentStatus}
              onChange={(event) => setPaymentStatus(event.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none transition focus:border-[#003b93] focus:bg-white"
            >
              <option value="">Tất cả thanh toán</option>
              <option value="PENDING">Chờ xử lý</option>
              <option value="PAID">Đã thanh toán</option>
              <option value="FAILED">Thất bại</option>
              <option value="CANCELLED">Đã hủy</option>
              <option value="REFUNDED">Đã hoàn tiền</option>
            </select>
          </label>

          <label className="space-y-2">
            <span className="block text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Tìm kiếm</span>
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Mã đơn, khách hàng, email, số điện thoại"
              className="w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none transition focus:border-[#003b93] focus:bg-white"
            />
          </label>

          <div className="flex items-end gap-2">
            <button
              type="button"
              onClick={resetFilters}
              className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 transition hover:bg-slate-50"
            >
              <RotateCcw className="h-4 w-4" />
              <span>Đặt lại</span>
            </button>
            <button
              type="button"
              onClick={applyFilters}
              className="rounded-lg bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-800"
            >
              Áp dụng
            </button>
          </div>
        </div>
      </section>

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        {loading ? (
          <div className="px-6 py-10 text-sm text-slate-500">Đang tải đơn hàng...</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left">
              <thead className="bg-slate-50">
                <tr>
                  <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Đơn hàng</th>
                  <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Khách hàng</th>
                  <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Sản phẩm</th>
                  <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Trạng thái</th>
                  <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Thanh toán</th>
                  <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400 text-right">Tổng tiền</th>
                  <th className="px-6 py-4 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {orders.map((order) => (
                  <tr key={order.id} className="hover:bg-slate-50">
                    <td className="px-6 py-4">
                      <p className="font-bold text-slate-950">{order.orderNumber}</p>
                      <p className="mt-1 text-xs text-slate-400">{formatDate(order.createdAt)}</p>
                    </td>
                    <td className="px-6 py-4">
                      <p className="text-sm font-semibold text-slate-900">{order.shipment?.recipientName || "Đơn khách vãng lai"}</p>
                      <p className="mt-1 text-xs text-slate-400">{order.shipment?.phoneNumber || order.customerEmail || "-"}</p>
                    </td>
                    <td className="px-6 py-4">
                      <p className="text-sm font-semibold text-slate-900">{order.items[0]?.productName || "Sản phẩm"}</p>
                      <p className="mt-1 text-xs text-slate-400">
                        {order.items.length > 1 ? `${order.items.length} sản phẩm` : formatManagerLabel(order.paymentMethod)}
                      </p>
                    </td>
                    <td className="px-6 py-4">
                      <ManagerStatusBadge label={order.status} tone={orderTone(order.status)} />
                    </td>
                    <td className="px-6 py-4">
                      <ManagerStatusBadge label={order.paymentStatus} tone={paymentTone(order.paymentStatus)} />
                    </td>
                    <td className="px-6 py-4 text-right font-bold text-slate-950">{formatCurrency(order.totalAmount)}</td>
                    <td className="px-6 py-4">
                      <div className="flex justify-end">
                        <Link
                          to={`/manager/orders/${order.id}`}
                          className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
                        >
                          <Eye className="h-4 w-4" />
                          <span>Xem</span>
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
};

export default ManagerOrdersPage;
