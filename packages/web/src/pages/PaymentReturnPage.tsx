import { paymentAPI } from "@ebike/shared-code/api";
import type { VnPayReturnResponse } from "@ebike/shared-code/types";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";

const PaymentReturnPage = () => {
  const location = useLocation();
  const [result, setResult] = useState<VnPayReturnResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadResult = async () => {
      const queryString = location.search.replace(/^\?/, "");
      if (!queryString) {
        setError("Không tìm thấy thông tin thanh toán.");
        setLoading(false);
        return;
      }

      try {
        const data = await paymentAPI.getVnPayReturnResult(queryString);
        setResult(data);
      } catch (requestError) {
        setError(requestError instanceof Error ? requestError.message : "Không thể kiểm tra kết quả thanh toán.");
      } finally {
        setLoading(false);
      }
    };

    void loadResult();
  }, [location.search]);

  const success = Boolean(result?.success);

  return (
    <div className="mx-auto flex min-h-screen max-w-3xl flex-col items-center justify-center px-6 py-32 text-center">
      {loading ? (
        <>
          <Loader2 className="mb-6 h-12 w-12 animate-spin text-primary" />
          <h1 className="text-3xl font-bold tracking-tight">Đang kiểm tra thanh toán</h1>
          <p className="mt-3 text-muted-foreground">Vui lòng đợi trong giây lát.</p>
        </>
      ) : (
        <>
          {success ? (
            <CheckCircle2 className="mb-6 h-16 w-16 text-emerald-600" />
          ) : (
            <XCircle className="mb-6 h-16 w-16 text-red-600" />
          )}
          <h1 className="text-4xl font-bold tracking-tight">
            {success ? "Thanh toan thanh cong" : "Thanh toan chua hoan tat"}
          </h1>
          <p className="mt-4 max-w-xl text-muted-foreground">
            {success
              ? "Đơn hàng của bạn đã được xác nhận thanh toán qua VNPay."
              : error || "Giao dịch không thành công hoặc chữ ký phản hồi không hợp lệ."}
          </p>

          {result ? (
            <div className="mt-8 w-full rounded-lg border border-outline-variant/20 bg-white p-6 text-left text-sm">
              <div className="flex justify-between gap-4 py-2">
                <span className="text-muted-foreground">Mã đơn</span>
                <span className="font-medium">{result.orderNumber || "-"}</span>
              </div>
              <div className="flex justify-between gap-4 py-2">
                <span className="text-muted-foreground">Mã giao dịch</span>
                <span className="font-medium">{result.transactionNo || result.txnRef || "-"}</span>
              </div>
              <div className="flex justify-between gap-4 py-2">
                <span className="text-muted-foreground">Số tiền</span>
                <span className="font-medium">{result.amount ? `${result.amount.toLocaleString("vi-VN")}d` : "-"}</span>
              </div>
              <div className="flex justify-between gap-4 py-2">
                <span className="text-muted-foreground">Trạng thái</span>
                <span className="font-medium">{result.paymentStatus || "-"}</span>
              </div>
            </div>
          ) : null}

          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link to="/customer/orders" className="rounded-lg bg-primary px-5 py-3 text-sm font-bold text-white">
              Xem đơn hàng
            </Link>
            <Link to="/products" className="rounded-lg border border-outline-variant/30 px-5 py-3 text-sm font-bold">
              Tiếp tục mua sắm
            </Link>
          </div>
        </>
      )}
    </div>
  );
};

export default PaymentReturnPage;
