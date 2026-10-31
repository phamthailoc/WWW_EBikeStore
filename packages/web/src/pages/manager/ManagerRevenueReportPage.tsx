import { useCallback, useEffect, useMemo, useState } from "react";
import { BarChart3, CheckCircle2, ShoppingCart, TrendingDown, TrendingUp } from "lucide-react";
import { managerAPI } from "@ebike/shared-code/api";
import type { ManagerRevenueReport } from "@ebike/shared-code/types";

type ReportPeriod = "day" | "month" | "quarter" | "year" | "custom";

const periodOptions: { value: ReportPeriod; label: string }[] = [
  { value: "day", label: "Theo ngày" },
  { value: "month", label: "Theo tháng" },
  { value: "quarter", label: "Theo quý" },
  { value: "year", label: "Theo năm" },
  { value: "custom", label: "Tùy chọn" }
];

const toIsoDate = (date: Date) => date.toISOString().slice(0, 10);

const defaultCustomRange = () => {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 29);
  return { from: toIsoDate(from), to: toIsoDate(to) };
};

const formatCurrency = (value: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(value);

const formatDateLabel = (value: string) => {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split("-");
    return `${day}/${month}/${year}`;
  }
  if (/^\d{4}-\d{2}$/.test(value)) {
    const [year, month] = value.split("-");
    return `Tháng ${month}/${year}`;
  }
  if (/^\d{4}-Q[1-4]$/.test(value)) {
    const [year, quarter] = value.split("-");
    return `${quarter} ${year}`;
  }
  return value;
};

const emptyReport: ManagerRevenueReport = {
  periodType: "DAY",
  fromDate: "",
  toDate: "",
  totalOrders: 0,
  successfulOrders: 0,
  totalRevenue: 0,
  breakdown: [],
  topProducts: [],
  slowProducts: []
};

const normalizeReport = (data: Partial<ManagerRevenueReport>): ManagerRevenueReport => ({
  ...emptyReport,
  ...data,
  breakdown: data.breakdown ?? [],
  topProducts: data.topProducts ?? [],
  slowProducts: data.slowProducts ?? []
});

const ManagerRevenueReportPage = () => {
  const [period, setPeriod] = useState<ReportPeriod>("day");
  const [customRange, setCustomRange] = useState(defaultCustomRange);
  const [report, setReport] = useState<ManagerRevenueReport>(emptyReport);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadReport = useCallback(async () => {
    try {
      setLoading(true);
      setError("");

      const params =
        period === "custom"
          ? { period, from: customRange.from, to: customRange.to }
          : { period };

      setReport(normalizeReport(await managerAPI.getRevenueReport(params)));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Không thể tải báo cáo doanh thu.");
    } finally {
      setLoading(false);
    }
  }, [customRange.from, customRange.to, period]);

  useEffect(() => {
    if (period !== "custom") {
      void loadReport();
    }
  }, [loadReport, period]);

  const stats = useMemo(
    () => [
      {
        label: "Tổng đơn hàng",
        value: String(report.totalOrders),
        hint: "Không tính đơn đã hủy",
        icon: ShoppingCart,
        tone: "text-blue-700 bg-blue-50"
      },
      {
        label: "Tổng doanh thu",
        value: formatCurrency(report.totalRevenue),
        hint: "Chỉ tính đơn đã thanh toán",
        icon: TrendingUp,
        tone: "text-green-700 bg-green-50"
      },
      {
        label: "Đơn thành công",
        value: String(report.successfulOrders),
        hint: "Trạng thái đã giao",
        icon: CheckCircle2,
        tone: "text-emerald-700 bg-emerald-50"
      }
    ],
    [report]
  );

  const maxRevenue = useMemo(
    () => Math.max(...report.breakdown.map((point) => point.revenue), 1),
    [report.breakdown]
  );

  const rangeLabel =
    report.fromDate && report.toDate
      ? `${formatDateLabel(report.fromDate)} – ${formatDateLabel(report.toDate)}`
      : "";

  return (
    <div className="space-y-6">
      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="text-xl font-bold text-slate-950">Báo cáo doanh thu</h2>
            <p className="mt-1 text-sm text-slate-500">
              Thống kê theo khoảng thời gian, loại trừ các đơn đã hủy.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {periodOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setPeriod(option.value)}
                className={`rounded-lg px-4 py-2 text-sm font-semibold transition ${
                  period === option.value
                    ? "bg-[#003b93] text-white"
                    : "border border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        {period === "custom" && (
          <div className="mt-5 flex flex-col gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:items-end">
            <label className="flex flex-1 flex-col gap-1 text-sm font-semibold text-slate-700">
              Từ ngày
              <input
                type="date"
                value={customRange.from}
                onChange={(event) => setCustomRange((current) => ({ ...current, from: event.target.value }))}
                className="rounded-lg border border-slate-200 px-3 py-2 font-normal outline-none transition focus:border-[#003b93]"
              />
            </label>
            <label className="flex flex-1 flex-col gap-1 text-sm font-semibold text-slate-700">
              Đến ngày
              <input
                type="date"
                value={customRange.to}
                onChange={(event) => setCustomRange((current) => ({ ...current, to: event.target.value }))}
                className="rounded-lg border border-slate-200 px-3 py-2 font-normal outline-none transition focus:border-[#003b93]"
              />
            </label>
            <button
              type="button"
              onClick={() => void loadReport()}
              className="rounded-lg bg-[#003b93] px-5 py-2.5 text-sm font-semibold text-white transition hover:opacity-90"
            >
              Áp dụng
            </button>
          </div>
        )}

        {rangeLabel && !loading && (
          <p className="mt-4 text-sm text-slate-500">
            Khoảng thời gian: <span className="font-semibold text-slate-700">{rangeLabel}</span>
          </p>
        )}
      </section>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <section className="grid gap-4 md:grid-cols-3">
        {stats.map((stat) => (
          <article key={stat.label} className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <div className={`inline-flex rounded-lg p-2 ${stat.tone}`}>
              <stat.icon className="h-5 w-5" />
            </div>
            <p className="mt-6 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">{stat.label}</p>
            <p className="mt-2 text-2xl font-bold text-slate-950">{loading ? "..." : stat.value}</p>
            <p className="mt-1 text-xs text-slate-500">{stat.hint}</p>
          </article>
        ))}
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-[#eef4ff] p-2 text-[#003b93]">
            <BarChart3 className="h-5 w-5" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-950">Biểu đồ doanh thu</h3>
            <p className="text-sm text-slate-500">Doanh thu và số đơn theo từng mốc thời gian.</p>
          </div>
        </div>

        {loading ? (
          <div className="mt-10 text-sm text-slate-500">Đang tải dữ liệu biểu đồ...</div>
        ) : report.breakdown.length === 0 ? (
          <div className="mt-10 text-sm text-slate-500">Chưa có dữ liệu trong khoảng thời gian này.</div>
        ) : (
          <div className="mt-8 overflow-x-auto pb-2">
            <div className="flex min-w-full items-end gap-3" style={{ minWidth: `${Math.max(report.breakdown.length * 56, 320)}px` }}>
              {report.breakdown.map((point) => {
                const height = Math.max((point.revenue / maxRevenue) * 180, point.revenue > 0 ? 8 : 0);

                return (
                  <div key={point.label} className="flex min-w-[48px] flex-1 flex-col items-center gap-2">
                    <p className="text-[10px] font-semibold text-slate-500">{formatCurrency(point.revenue)}</p>
                    <div className="flex h-[180px] w-full items-end justify-center">
                      <div
                        className="w-full max-w-[40px] rounded-t-md bg-[#003b93] transition-all"
                        style={{ height: `${height}px` }}
                        title={`${point.orderCount} đơn`}
                      />
                    </div>
                    <p className="text-center text-[11px] font-semibold text-slate-700">{formatDateLabel(point.label)}</p>
                    <p className="text-center text-[10px] text-slate-400">{point.orderCount} đơn</p>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </section>

      <section className="grid gap-6 md:grid-cols-2">
        <article className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="rounded-lg bg-emerald-50 p-2 text-emerald-700">
              <TrendingUp className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-slate-950">Sản phẩm bán chạy</h3>
              <p className="text-sm text-slate-500">Top 10 theo doanh thu từ đơn đã thanh toán.</p>
            </div>
          </div>

          {loading ? (
            <div className="mt-8 text-sm text-slate-500">Đang tải danh sách sản phẩm...</div>
          ) : report.topProducts.length === 0 ? (
            <div className="mt-8 text-sm text-slate-500">Chưa có sản phẩm nào trong khoảng thời gian này.</div>
          ) : (
            <div className="mt-6 space-y-3">
              {report.topProducts.map((product, index) => (
                <div
                  key={product.productId}
                  className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 bg-slate-50 px-4 py-3"
                >
                  <div className="min-w-0">
                    <p className="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">#{index + 1}</p>
                    <p className="truncate text-sm font-bold text-slate-900">{product.productName}</p>
                    <p className="mt-1 text-xs text-slate-500">Đã bán: {product.quantitySold}</p>
                  </div>
                  <p className="shrink-0 text-sm font-bold text-[#003b93]">{formatCurrency(product.revenue)}</p>
                </div>
              ))}
            </div>
          )}
        </article>

        <article className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="rounded-lg bg-amber-50 p-2 text-amber-700">
              <TrendingDown className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-slate-950">Sản phẩm không bán chạy</h3>
              <p className="text-sm text-slate-500">Top 10 ít bán nhất, gồm cả sản phẩm chưa có đơn trong kỳ.</p>
            </div>
          </div>

          {loading ? (
            <div className="mt-8 text-sm text-slate-500">Đang tải danh sách sản phẩm...</div>
          ) : report.slowProducts.length === 0 ? (
            <div className="mt-8 text-sm text-slate-500">Chưa có sản phẩm nào để thống kê.</div>
          ) : (
            <div className="mt-6 space-y-3">
              {report.slowProducts.map((product, index) => (
                <div
                  key={product.productId}
                  className="flex items-center justify-between gap-3 rounded-lg border border-amber-100 bg-amber-50/40 px-4 py-3"
                >
                  <div className="min-w-0">
                    <p className="text-xs font-bold uppercase tracking-[0.16em] text-amber-600">#{index + 1}</p>
                    <p className="truncate text-sm font-bold text-slate-900">{product.productName}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {product.quantitySold === 0 ? "Chưa bán trong kỳ" : `Đã bán: ${product.quantitySold}`}
                    </p>
                  </div>
                  <p className="shrink-0 text-sm font-bold text-amber-700">{formatCurrency(product.revenue)}</p>
                </div>
              ))}
            </div>
          )}
        </article>
      </section>
    </div>
  );
};

export default ManagerRevenueReportPage;
