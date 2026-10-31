import { useEffect, useMemo, useRef, useState } from "react";
import { AlertCircle, CheckCircle2, Clock3, CreditCard, ReceiptText, XCircle } from "lucide-react";
import { orderAPI, paymentAPI } from "@ebike/shared-code/api";
import { useAppSelector } from "@ebike/shared-code/redux";
import type { Order, PaymentHistoryItem } from "@ebike/shared-code/types";
import SectionShell from "../../components/common/SectionShell";

type PaymentFilter = "ALL" | "PAID" | "FAILED" | "PENDING";

const FILTERS: Array<{ value: PaymentFilter; label: string }> = [
  { value: "ALL", label: "Tất cả" },
  { value: "PAID", label: "Thành công" },
  { value: "FAILED", label: "Thất bại" },
  { value: "PENDING", label: "Chờ xử lý" }
];

const PAYMENT_STATUS_LABELS: Record<string, string> = {
  PENDING: "Chờ xử lý",
  AUTHORIZED: "Đã xác thực",
  PAID: "Thành công",
  FAILED: "Thất bại",
  CANCELLED: "Đã hủy",
  REFUNDED: "Đã hoàn tiền"
};

const PAYMENT_METHOD_LABELS: Record<string, string> = {
  PAY_LATER: "Thanh toán sau",
  VNPAY: "VNPay"
};

const STATUS_CLASSES: Record<string, string> = {
  PENDING: "border-amber-200 bg-amber-50 text-amber-700",
  AUTHORIZED: "border-blue-200 bg-blue-50 text-blue-700",
  PAID: "border-emerald-200 bg-emerald-50 text-emerald-700",
  FAILED: "border-rose-200 bg-rose-50 text-rose-700",
  CANCELLED: "border-slate-200 bg-slate-50 text-slate-700",
  REFUNDED: "border-violet-200 bg-violet-50 text-violet-700"
};

const formatMoney = (amount: number, currency: string) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: currency || "VND",
    maximumFractionDigits: 0
  }).format(amount);

const formatDateTime = (value?: string | null) => (value ? new Date(value).toLocaleString("vi-VN") : "Chưa cập nhật");

const statusIcon = (status: string) => {
  if (status === "PAID") {
    return <CheckCircle2 className="h-4 w-4" />;
  }
  if (status === "FAILED" || status === "CANCELLED") {
    return <XCircle className="h-4 w-4" />;
  }
  if (status === "PENDING") {
    return <Clock3 className="h-4 w-4" />;
  }
  return <AlertCircle className="h-4 w-4" />;
};

const orderToPaymentHistoryItem = (order: Order): PaymentHistoryItem | null => {
  if (!order.paymentMethod && !order.paymentStatus) {
    return null;
  }

  return {
    id: order.id,
    orderId: order.id,
    orderNumber: order.orderNumber,
    transactionReference: order.orderNumber,
    amount: order.totalAmount,
    currency: "VND",
    paymentMethod: order.paymentMethod || "PAY_LATER",
    paymentStatus: order.paymentStatus || "PENDING",
    providerTxnId: null,
    paidAt: null,
    createdAt: order.createdAt
  };
};

const loadPaymentHistoryFromOrders = async (statusFilter: PaymentFilter) => {
  const orders = await orderAPI.list();
  return orders
    .map(orderToPaymentHistoryItem)
    .filter((payment): payment is PaymentHistoryItem => payment !== null)
    .filter((payment) => statusFilter === "ALL" || payment.paymentStatus === statusFilter);
};

const CustomerPaymentHistoryPage = () => {
  const authUser = useAppSelector((state) => state.auth.user);
  const [payments, setPayments] = useState<PaymentHistoryItem[]>([]);
  const [statusFilter, setStatusFilter] = useState<PaymentFilter>("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const latestRequestId = useRef(0);

  useEffect(() => {
    const requestId = latestRequestId.current + 1;
    latestRequestId.current = requestId;
    setError(null);

    const loadPayments = async () => {
      if (!authUser?.id) {
        setPayments([]);
        setLoading(false);
        return;
      }

      setLoading(true);
      try {
        const data = await paymentAPI.listHistory(statusFilter === "ALL" ? undefined : statusFilter);
        if (latestRequestId.current !== requestId) {
          return;
        }
        setPayments(data);
      } catch (fetchError) {
        if (latestRequestId.current !== requestId) {
          return;
        }
        try {
          const fallbackData = await loadPaymentHistoryFromOrders(statusFilter);
          if (latestRequestId.current !== requestId) {
            return;
          }
          setPayments(fallbackData);
          setError(null);
        } catch (fallbackError) {
          setError(fallbackError instanceof Error ? fallbackError.message : "Không thể tải lịch sử thanh toán.");
          setPayments([]);
        }
      } finally {
        if (latestRequestId.current === requestId) {
          setLoading(false);
        }
      }
    };

    void loadPayments();
  }, [authUser?.id, statusFilter]);

  const summary = useMemo(
    () => ({
      total: payments.length,
      paid: payments.filter((payment) => payment.paymentStatus === "PAID").length,
      pending: payments.filter((payment) => payment.paymentStatus === "PENDING").length,
      failed: payments.filter((payment) => payment.paymentStatus === "FAILED").length
    }),
    [payments]
  );

  return (
    <SectionShell
      eyebrow="Lịch sử thanh toán"
      title="Theo dõi trạng thái các giao dịch của bạn."
      description="Xem mã giao dịch, số tiền, phương thức, trạng thái và thời gian thanh toán của từng đơn hàng."
    >
      <div className="px-6 pb-10">
        {!authUser?.id ? (
          <div className="rounded-lg bg-gray-50 p-6 text-center text-gray-600">
            Bạn cần đăng nhập để xem lịch sử thanh toán.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
              <div className="rounded-lg border border-slate-200 bg-white p-4">
                <p className="text-sm text-slate-500">Tổng giao dịch</p>
                <p className="mt-2 text-2xl font-bold text-slate-950">{summary.total}</p>
              </div>
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4">
                <p className="text-sm text-emerald-700">Thành công</p>
                <p className="mt-2 text-2xl font-bold text-emerald-800">{summary.paid}</p>
              </div>
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                <p className="text-sm text-amber-700">Chờ xử lý</p>
                <p className="mt-2 text-2xl font-bold text-amber-800">{summary.pending}</p>
              </div>
              <div className="rounded-lg border border-rose-200 bg-rose-50 p-4">
                <p className="text-sm text-rose-700">Thất bại</p>
                <p className="mt-2 text-2xl font-bold text-rose-800">{summary.failed}</p>
              </div>
            </div>

            <div className="flex flex-wrap gap-2 rounded-lg border border-slate-200 bg-white p-2">
              {FILTERS.map((filter) => (
                <button
                  key={filter.value}
                  type="button"
                  onClick={() => setStatusFilter(filter.value)}
                  className={`rounded-md px-4 py-2 text-sm font-semibold transition ${
                    statusFilter === filter.value
                      ? "bg-slate-950 text-white"
                      : "text-slate-600 hover:bg-slate-100 hover:text-slate-950"
                  }`}
                >
                  {filter.label}
                </button>
              ))}
            </div>

            {loading ? (
              <div className="rounded-lg bg-gray-50 p-6 text-center text-gray-600">Đang tải lịch sử thanh toán...</div>
            ) : error ? (
              <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-red-700">{error}</div>
            ) : payments.length === 0 ? (
              <div className="rounded-lg bg-gray-50 p-8 text-center text-gray-600">
                Không có giao dịch nào phù hợp với bộ lọc hiện tại.
              </div>
            ) : (
              <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
                <div className="hidden grid-cols-[1.25fr_1fr_1fr_1fr_1fr] gap-4 border-b border-slate-200 bg-slate-50 px-5 py-3 text-xs font-bold uppercase tracking-wide text-slate-500 lg:grid">
                  <span>Mã giao dịch</span>
                  <span>Số tiền</span>
                  <span>Phương thức</span>
                  <span>Trạng thái</span>
                  <span>Thời gian</span>
                </div>
                <div className="divide-y divide-slate-100">
                  {payments.map((payment) => {
                    const transactionCode = payment.transactionReference || payment.providerTxnId || `PAY-${payment.id}`;
                    return (
                      <article key={payment.id} className="grid gap-4 px-5 py-5 lg:grid-cols-[1.25fr_1fr_1fr_1fr_1fr] lg:items-center">
                        <div>
                          <div className="flex items-center gap-2 font-bold text-slate-950">
                            <ReceiptText className="h-4 w-4 text-slate-500" />
                            <span>{transactionCode}</span>
                          </div>
                          <p className="mt-1 text-sm text-slate-500">Đơn hàng {payment.orderNumber}</p>
                        </div>

                        <div>
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 lg:hidden">Số tiền</p>
                          <p className="font-bold text-slate-950">{formatMoney(payment.amount, payment.currency)}</p>
                        </div>

                        <div>
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 lg:hidden">Phương thức</p>
                          <div className="flex items-center gap-2 text-sm font-semibold text-slate-700">
                            <CreditCard className="h-4 w-4 text-slate-500" />
                            <span>{PAYMENT_METHOD_LABELS[payment.paymentMethod] || payment.paymentMethod}</span>
                          </div>
                        </div>

                        <div>
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 lg:hidden">Trạng thái</p>
                          <span
                            className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${
                              STATUS_CLASSES[payment.paymentStatus] || "border-slate-200 bg-slate-50 text-slate-700"
                            }`}
                          >
                            {statusIcon(payment.paymentStatus)}
                            {PAYMENT_STATUS_LABELS[payment.paymentStatus] || payment.paymentStatus}
                          </span>
                        </div>

                        <div>
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400 lg:hidden">Thời gian</p>
                          <p className="text-sm font-medium text-slate-700">{formatDateTime(payment.paidAt || payment.createdAt)}</p>
                        </div>
                      </article>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </SectionShell>
  );
};

export default CustomerPaymentHistoryPage;
