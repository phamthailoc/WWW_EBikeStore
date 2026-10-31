import { useEffect, useState } from "react";
import { ShieldCheck, Star } from "lucide-react";
import { managerAPI } from "@ebike/shared-code/api";
import type { ManagerCustomer } from "@ebike/shared-code/types";

const formatCurrency = (value: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(value);

const ManagerCustomersPage = () => {
  const [customers, setCustomers] = useState<ManagerCustomer[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadCustomers = async (query?: string) => {
    try {
      setLoading(true);
      setCustomers(await managerAPI.getCustomers(query));
      setError("");
    } catch (customersError) {
      setError(customersError instanceof Error ? customersError.message : "Không thể tải danh sách khách hàng.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadCustomers();
  }, []);

  return (
    <div className="space-y-6">
      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="text-sm text-slate-500">Tra cứu tài khoản khách hàng và giá trị mua hàng đã thanh toán.</p>
          </div>
          <div className="flex w-full gap-2 md:w-auto">
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Tìm theo tên đăng nhập, email, họ tên"
              className="w-full rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm outline-none transition focus:border-[#003b93] focus:bg-white md:w-72"
            />
            <button
              type="button"
              onClick={() => void loadCustomers(search.trim() || undefined)}
              className="rounded-lg bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-800"
            >
              Tìm kiếm
            </button>
          </div>
        </div>
      </section>

      {loading ? (
        <div className="rounded-lg border border-slate-200 bg-white px-6 py-10 text-sm text-slate-500">Đang tải khách hàng...</div>
      ) : (
        <section className="grid gap-4 md:grid-cols-2 2xl:grid-cols-3">
          {customers.map((customer) => {
            const fullName = [customer.firstName, customer.lastName].filter(Boolean).join(" ").trim() || customer.username;
            return (
              <article key={customer.id} className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
                <div className="flex items-start justify-between">
                  <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-blue-50 text-lg font-bold text-[#003b93]">
                    {fullName
                      .split(" ")
                      .map((part) => part[0])
                      .join("")
                      .slice(0, 2)
                      .toUpperCase()}
                  </div>
                  {customer.verified && <ShieldCheck className="h-4 w-4 text-[#003b93]" />}
                </div>

                <div className="mt-5">
                  <h3 className="text-lg font-bold text-slate-950">{fullName}</h3>
                  <p className="mt-1 text-sm text-slate-500">{customer.email}</p>
                </div>

                <div className="mt-6 grid grid-cols-2 gap-4 border-t border-slate-100 pt-5">
                  <div>
                    <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Tổng đơn hàng</p>
                    <p className="mt-2 text-base font-bold text-slate-950">{customer.orderCount}</p>
                  </div>
                  <div>
                    <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Tổng chi tiêu</p>
                    <p className="mt-2 text-base font-bold text-slate-950">{formatCurrency(customer.totalSpent)}</p>
                  </div>
                </div>

                <div className="mt-5 flex items-center justify-between">
                  <div className="flex items-center gap-1.5 text-sm font-semibold text-slate-600">
                    <Star className="h-4 w-4 fill-orange-400 text-orange-400" />
                    <span>{customer.active ? "Đang hoạt động" : "Ngừng hoạt động"}</span>
                  </div>
                  <span
                    className={`rounded-md px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.16em] ${
                      customer.verified ? "bg-green-50 text-green-700" : "bg-slate-100 text-slate-600"
                    }`}
                  >
                    {customer.verified ? "Đã xác minh" : "Chưa xác minh"}
                  </span>
                </div>
              </article>
            );
          })}
        </section>
      )}
    </div>
  );
};

export default ManagerCustomersPage;
