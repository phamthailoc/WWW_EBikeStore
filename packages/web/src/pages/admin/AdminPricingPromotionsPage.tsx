import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  BadgePercent,
  CalendarDays,
  Clock,
  MapPinOff,
  PlusCircle,
  Receipt,
  Save,
  TicketPercent,
  Timer,
  WalletCards,
  X
} from "lucide-react";
import { adminAPI } from "@ebike/shared-code/api";

type PricingRule = {
  id: number;
  code: string;
  name: string;
  description: string;
  value: number;
  unit: string;
  status: "active" | "scheduled" | "inactive";
  updatedAt: string;
};

type Promotion = {
  id: number;
  code: string;
  campaign: string;
  discount: string;
  usage: number;
  limit: number;
  status: "live" | "waiting" | "expired" | "disabled";
  period: string;
};

const initialPricingRules: PricingRule[] = [
  {
    id: 1,
    code: "UNLOCK_FEE",
    name: "Phí mở khóa",
    description: "Thu một lần khi người dùng bắt đầu chuyến đi.",
    value: 5000,
    unit: "VND/lượt",
    status: "active",
    updatedAt: "02/06/2026"
  },
  {
    id: 2,
    code: "MINUTE_RATE",
    name: "Giá thuê theo phút",
    description: "Áp dụng cho chuyến đi ngắn và tính lũy tiến theo thời gian.",
    value: 1800,
    unit: "VND/phút",
    status: "active",
    updatedAt: "01/06/2026"
  },
  {
    id: 3,
    code: "HOUR_RATE",
    name: "Gói theo giờ",
    description: "Mức trần cho khách thuê xe trong thời gian dài.",
    value: 45000,
    unit: "VND/giờ",
    status: "scheduled",
    updatedAt: "05/06/2026"
  },
  {
    id: 4,
    code: "WRONG_ZONE_FINE",
    name: "Phạt trả sai khu vực",
    description: "Áp dụng khi xe được trả ngoài vùng hợp lệ.",
    value: 30000,
    unit: "VND/lần",
    status: "active",
    updatedAt: "29/05/2026"
  },
  {
    id: 5,
    code: "OVERDUE_FINE",
    name: "Phạt quá hạn",
    description: "Thu thêm khi chuyến đi vượt thời gian giữ xe cho phép.",
    value: 12000,
    unit: "VND/15 phút",
    status: "active",
    updatedAt: "28/05/2026"
  }
];

const initialPromotions: Promotion[] = [
  {
    id: 1,
    code: "KINETIC20",
    campaign: "Chào hè xanh",
    discount: "20% tối đa 25.000đ",
    usage: 326,
    limit: 800,
    status: "live",
    period: "01/06 - 30/06/2026"
  },
  {
    id: 2,
    code: "STUDENT15",
    campaign: "Sinh viên đi học",
    discount: "15.000đ/chuyến",
    usage: 1020,
    limit: 2000,
    status: "live",
    period: "15/05 - 15/07/2026"
  },
  {
    id: 3,
    code: "WEEKEND",
    campaign: "Cuối tuần",
    discount: "10%",
    usage: 0,
    limit: 500,
    status: "waiting",
    period: "08/06 - 30/06/2026"
  }
];

const formatCurrency = (value: number) => `${value.toLocaleString("vi-VN")}đ`;

const statusLabel: Record<string, string> = {
  active: "Đang áp dụng",
  scheduled: "Đã lên lịch",
  live: "Đang chạy",
  waiting: "Chờ chạy",
  expired: "Hết hạn"
};

const formatDate = (value?: string | null) => (value ? new Date(value).toLocaleDateString("vi-VN") : "Chua dat");

type ApiPricingRule = Awaited<ReturnType<typeof adminAPI.getPricingRules>>[number];
type ApiPromotion = Awaited<ReturnType<typeof adminAPI.getPromotions>>[number];

const mapApiRule = (rule: ApiPricingRule): PricingRule => ({
  id: rule.id,
  code: rule.code,
  name: rule.name,
  description: rule.description,
  value: Number(rule.amount),
  unit: rule.unit,
  status: rule.status.toLowerCase() as PricingRule["status"],
  updatedAt: formatDate(rule.updatedAt)
});

const mapApiPromotion = (promotion: ApiPromotion): Promotion => ({
  id: promotion.id,
  code: promotion.code,
  campaign: promotion.campaignName,
  discount:
    promotion.discountType === "PERCENTAGE"
      ? `${Number(promotion.discountValue)}%${
          promotion.maxDiscountAmount ? ` toi da ${formatCurrency(Number(promotion.maxDiscountAmount))}` : ""
        }`
      : `${formatCurrency(Number(promotion.discountValue))}/chuyen`,
  usage: promotion.usageCount,
  limit: promotion.usageLimit,
  status: promotion.status.toLowerCase() as Promotion["status"],
  period: `${formatDate(promotion.startsAt)} - ${formatDate(promotion.endsAt)}`
});

const AdminPricingPromotionsPage = () => {
  const [pricingRules, setPricingRules] = useState(initialPricingRules);
  const [promotions, setPromotions] = useState(initialPromotions);
  const [selectedRuleId, setSelectedRuleId] = useState(initialPricingRules[0].id);
  const [ruleValue, setRuleValue] = useState(String(initialPricingRules[0].value));
  const [isPromoOpen, setIsPromoOpen] = useState(false);
  const [newPromo, setNewPromo] = useState({
    code: "",
    campaign: "",
    discount: "",
    limit: "500",
    period: "10/06 - 30/06/2026"
  });
  const [apiError, setApiError] = useState<string | null>(null);

  const selectedRule = pricingRules.find((rule) => rule.id === selectedRuleId) ?? pricingRules[0];

  useEffect(() => {
    let cancelled = false;

    const loadAdminPricing = async () => {
      try {
        const [rules, promos] = await Promise.all([adminAPI.getPricingRules(), adminAPI.getPromotions()]);
        if (cancelled) return;
        setPricingRules(rules.map(mapApiRule));
        setPromotions(promos.map(mapApiPromotion));
        setSelectedRuleId(rules[0]?.id ?? initialPricingRules[0].id);
        setRuleValue(String(rules[0]?.amount ?? initialPricingRules[0].value));
        setApiError(null);
      } catch (error) {
        if (!cancelled) {
          setApiError(error instanceof Error ? error.message : "Khong the tai du lieu admin tu API.");
        }
      }
    };

    loadAdminPricing();

    return () => {
      cancelled = true;
    };
  }, []);

  const totals = useMemo(() => {
    const livePromos = promotions.filter((promo) => promo.status === "live");
    const totalLimit = promotions.reduce((sum, promo) => sum + promo.limit, 0);
    const totalUsage = promotions.reduce((sum, promo) => sum + promo.usage, 0);

    return {
      livePromos: livePromos.length,
      totalUsage,
      usageRate: totalLimit ? Math.round((totalUsage / totalLimit) * 100) : 0
    };
  }, [promotions]);

  const handleRuleChange = (id: number) => {
    const nextRule = pricingRules.find((rule) => rule.id === id);

    if (!nextRule) return;
    setSelectedRuleId(id);
    setRuleValue(String(nextRule.value));
  };

  const handleSaveRule = (event: FormEvent) => {
    event.preventDefault();
    const parsedValue = Number(ruleValue);

    if (!Number.isFinite(parsedValue) || parsedValue <= 0) return;

    setPricingRules((current) =>
      current.map((rule) =>
        rule.id === selectedRuleId
          ? {
              ...rule,
              value: parsedValue,
              status: "active",
              updatedAt: "02/06/2026"
            }
          : rule
      )
    );
    adminAPI
      .updatePricingRule(selectedRuleId, { amount: parsedValue, status: "ACTIVE" })
      .then((saved) => {
        setPricingRules((current) => current.map((rule) => (rule.id === saved.id ? mapApiRule(saved) : rule)));
        setApiError(null);
      })
      .catch((error) => setApiError(error instanceof Error ? error.message : "Khong the luu bieu phi."));
  };

  const handleAddPromo = (event: FormEvent) => {
    event.preventDefault();

    if (!newPromo.code.trim() || !newPromo.campaign.trim() || !newPromo.discount.trim()) return;

    setPromotions((current) => [
      {
        id: Date.now(),
        code: newPromo.code.trim().toUpperCase(),
        campaign: newPromo.campaign.trim(),
        discount: newPromo.discount.trim(),
        usage: 0,
        limit: Number(newPromo.limit) || 500,
        status: "waiting",
        period: newPromo.period.trim() || "Chưa đặt lịch"
      },
      ...current
    ]);
    adminAPI
      .createPromotion({
        code: newPromo.code.trim().toUpperCase(),
        campaignName: newPromo.campaign.trim(),
        discountType: newPromo.discount.includes("%") ? "PERCENTAGE" : "FIXED_AMOUNT",
        discountValue: Number.parseFloat(newPromo.discount.replace(/[^\d.]/g, "")) || 1,
        maxDiscountAmount: null,
        usageLimit: Number(newPromo.limit) || 500,
        status: "WAITING",
        startsAt: null,
        endsAt: null
      })
      .then(async () => {
        const promos = await adminAPI.getPromotions();
        setPromotions(promos.map(mapApiPromotion));
        setApiError(null);
      })
      .catch((error) => setApiError(error instanceof Error ? error.message : "Khong the tao campaign."));
    setNewPromo({ code: "", campaign: "", discount: "", limit: "500", period: "10/06 - 30/06/2026" });
    setIsPromoOpen(false);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <h2 className="font-display text-3xl font-bold tracking-tight text-slate-950">Quản lý giá và khuyến mãi</h2>
          <p className="mt-1 text-sm text-slate-500">
            Cấu hình phí thuê xe, phí phạt và voucher/campaign cho hệ thống e-bike.
          </p>
        </div>
        <button
          onClick={() => setIsPromoOpen(true)}
          className="inline-flex items-center justify-center gap-2 rounded-lg bg-[#0051c3] px-5 py-2.5 text-xs font-bold uppercase tracking-wider text-white shadow-md shadow-blue-200 transition hover:bg-blue-800"
        >
          <PlusCircle className="h-4 w-4" />
          Thêm campaign
        </button>
      </div>

      {apiError && (
        <div className="rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-800">
          API admin chua san sang hoac tai khoan hien tai chua co quyen: {apiError}
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
        <div className="rounded-xl border border-slate-100 bg-white p-5 shadow-[0_4px_12px_rgba(15,23,42,0.02)]">
          <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Phí mở khóa</p>
          <p className="mt-2 font-display text-3xl font-bold text-slate-950">
            {formatCurrency(pricingRules.find((rule) => rule.code === "UNLOCK_FEE")?.value ?? 0)}
          </p>
        </div>
        <div className="rounded-xl border border-slate-100 bg-white p-5 shadow-[0_4px_12px_rgba(15,23,42,0.02)]">
          <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Campaign đang chạy</p>
          <p className="mt-2 font-display text-3xl font-bold text-slate-950">{totals.livePromos}</p>
        </div>
        <div className="rounded-xl border border-slate-100 bg-[#0051c3] p-5 text-white shadow-lg shadow-blue-200">
          <p className="text-xs font-bold uppercase tracking-wider text-blue-100">Tỷ lệ dùng voucher</p>
          <p className="mt-2 font-display text-3xl font-bold">{totals.usageRate}%</p>
        </div>
      </div>

      <div className="grid grid-cols-12 gap-6">
        <div className="col-span-12 rounded-xl border border-slate-100 bg-white p-6 shadow-[0_4px_12px_rgba(15,23,42,0.02)] lg:col-span-8">
          <div className="mb-5 flex items-center justify-between">
            <h3 className="font-display text-lg font-bold text-slate-900">Bảng cấu hình giá</h3>
            <span className="rounded-lg bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-400">
              {pricingRules.length} quy tắc
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-slate-50 text-[11px] uppercase tracking-wider text-slate-500">
                  <th className="rounded-l-lg px-4 py-3">Khoản phí</th>
                  <th className="px-4 py-3">Giá trị</th>
                  <th className="px-4 py-3">Trạng thái</th>
                  <th className="rounded-r-lg px-4 py-3">Cập nhật</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-sm">
                {pricingRules.map((rule) => (
                  <tr
                    key={rule.id}
                    onClick={() => handleRuleChange(rule.id)}
                    className="cursor-pointer transition hover:bg-slate-50/70"
                  >
                    <td className="px-4 py-4">
                      <div className="flex items-center gap-3">
                        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-[#0051c3]">
                          {rule.code === "WRONG_ZONE_FINE" ? (
                            <MapPinOff className="h-5 w-5" />
                          ) : rule.code === "OVERDUE_FINE" ? (
                            <Timer className="h-5 w-5" />
                          ) : (
                            <WalletCards className="h-5 w-5" />
                          )}
                        </div>
                        <div>
                          <p className="font-bold text-slate-900">{rule.name}</p>
                          <p className="mt-0.5 text-xs text-slate-400">{rule.description}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-4 font-semibold text-slate-700">
                      {formatCurrency(rule.value)} <span className="text-xs text-slate-400">/ {rule.unit.split("/")[1]}</span>
                    </td>
                    <td className="px-4 py-4">
                      <span
                        className={`rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider ${
                          rule.status === "active" ? "bg-emerald-50 text-emerald-700" : "bg-blue-50 text-blue-700"
                        }`}
                      >
                        {statusLabel[rule.status]}
                      </span>
                    </td>
                    <td className="px-4 py-4 text-xs font-medium text-slate-500">{rule.updatedAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <form
          onSubmit={handleSaveRule}
          className="col-span-12 rounded-xl border border-slate-100 bg-white p-6 shadow-[0_4px_12px_rgba(15,23,42,0.02)] lg:col-span-4"
        >
          <div className="mb-5 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-50 text-[#0051c3]">
              <Receipt className="h-5 w-5" />
            </div>
            <div>
              <h3 className="font-display font-bold text-slate-900">Sửa nhanh biểu phí</h3>
              <p className="text-xs text-slate-400">Click một dòng bên trái để chỉnh.</p>
            </div>
          </div>

          <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Loại phí</label>
          <select
            value={selectedRuleId}
            onChange={(event) => handleRuleChange(Number(event.target.value))}
            className="mt-1.5 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium outline-none focus:ring-2 focus:ring-[#0051c3]/20"
          >
            {pricingRules.map((rule) => (
              <option key={rule.id} value={rule.id}>
                {rule.name}
              </option>
            ))}
          </select>

          <label className="mt-4 block text-[10px] font-bold uppercase tracking-wider text-slate-400">Giá trị mới</label>
          <div className="relative mt-1.5">
            <input
              type="number"
              min="1"
              value={ruleValue}
              onChange={(event) => setRuleValue(event.target.value)}
              className="w-full rounded-lg border border-slate-200 px-3 py-2 pr-12 text-sm font-semibold outline-none focus:ring-2 focus:ring-[#0051c3]/20"
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-bold text-slate-400">VND</span>
          </div>

          <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50/50 p-4 text-xs leading-relaxed text-slate-600">
            {selectedRule.description} Đơn vị hiện tại: <span className="font-bold text-[#0051c3]">{selectedRule.unit}</span>.
          </div>

          <button
            type="submit"
            className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-[#0051c3] px-4 py-2.5 text-xs font-bold uppercase tracking-wider text-white transition hover:bg-blue-800"
          >
            <Save className="h-4 w-4" />
            Lưu biểu phí
          </button>
        </form>
      </div>

      <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-[0_4px_12px_rgba(15,23,42,0.02)]">
        <div className="mb-5 flex items-center justify-between">
          <h3 className="font-display text-lg font-bold text-slate-900">Voucher và campaign</h3>
          <TicketPercent className="h-5 w-5 text-[#0051c3]" />
        </div>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          {promotions.map((promo) => {
            const usagePercent = Math.min(Math.round((promo.usage / promo.limit) * 100), 100);

            return (
              <div key={promo.id} className="rounded-xl border border-slate-100 p-4 transition hover:border-blue-100 hover:shadow-sm">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Code</p>
                    <p className="mt-1 font-display text-lg font-bold text-slate-900">{promo.code}</p>
                  </div>
                  <span
                    className={`rounded px-2 py-1 text-[10px] font-bold uppercase ${
                      promo.status === "live"
                        ? "bg-emerald-50 text-emerald-700"
                        : promo.status === "waiting"
                          ? "bg-blue-50 text-blue-700"
                          : "bg-slate-100 text-slate-500"
                    }`}
                  >
                    {statusLabel[promo.status]}
                  </span>
                </div>

                <p className="mt-3 text-sm font-bold text-slate-800">{promo.campaign}</p>
                <p className="mt-1 text-xs text-slate-500">{promo.discount}</p>
                <div className="mt-4 flex items-center justify-between text-xs text-slate-500">
                  <span className="flex items-center gap-1">
                    <CalendarDays className="h-3.5 w-3.5" />
                    {promo.period}
                  </span>
                  <span>{promo.usage.toLocaleString("vi-VN")} / {promo.limit.toLocaleString("vi-VN")}</span>
                </div>
                <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div className="h-full rounded-full bg-[#0051c3]" style={{ width: `${usagePercent}%` }} />
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {isPromoOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md overflow-hidden rounded-xl border border-slate-100 bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-6 py-4">
              <h3 className="font-display text-lg font-bold text-slate-900">Thêm campaign mới</h3>
              <button onClick={() => setIsPromoOpen(false)} className="text-slate-400 hover:text-slate-700">
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleAddPromo} className="space-y-4 p-6">
              <div>
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Mã voucher</label>
                <input
                  required
                  value={newPromo.code}
                  onChange={(event) => setNewPromo((current) => ({ ...current, code: event.target.value }))}
                  placeholder="VD: GREENRIDE"
                  className="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm font-bold uppercase outline-none focus:ring-2 focus:ring-[#0051c3]/20"
                />
              </div>
              <div>
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Tên campaign</label>
                <input
                  required
                  value={newPromo.campaign}
                  onChange={(event) => setNewPromo((current) => ({ ...current, campaign: event.target.value }))}
                  className="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-[#0051c3]/20"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Ưu đãi</label>
                  <input
                    required
                    value={newPromo.discount}
                    onChange={(event) => setNewPromo((current) => ({ ...current, discount: event.target.value }))}
                    placeholder="20% tối đa 25.000đ"
                    className="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-[#0051c3]/20"
                  />
                </div>
                <div>
                  <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Giới hạn</label>
                  <input
                    type="number"
                    min="1"
                    value={newPromo.limit}
                    onChange={(event) => setNewPromo((current) => ({ ...current, limit: event.target.value }))}
                    className="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-[#0051c3]/20"
                  />
                </div>
              </div>
              <div>
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Thời gian</label>
                <input
                  value={newPromo.period}
                  onChange={(event) => setNewPromo((current) => ({ ...current, period: event.target.value }))}
                  className="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-[#0051c3]/20"
                />
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <button type="button" onClick={() => setIsPromoOpen(false)} className="px-4 py-2 text-xs font-bold uppercase text-slate-500">
                  Hủy
                </button>
                <button type="submit" className="rounded-lg bg-[#0051c3] px-5 py-2 text-xs font-bold uppercase tracking-wider text-white">
                  Thêm voucher
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
        <Clock className="h-4 w-4" />
        Thay đổi giá đang được mô phỏng trên frontend, sẵn sàng nối API khi backend có endpoint.
      </div>
    </div>
  );
};

export default AdminPricingPromotionsPage;
