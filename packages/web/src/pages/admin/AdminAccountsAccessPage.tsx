import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  CheckCircle2,
  Clock,
  Download,
  History,
  ListFilter,
  MoreVertical,
  RefreshCw,
  ShieldCheck,
  UserPlus,
  UserRound,
  Users,
  UserX,
  X
} from "lucide-react";
import { adminAPI } from "@ebike/shared-code/api";

type AccountRole = "SUPER_ADMIN" | "OPERATOR" | "SUPPORT" | "MAINTENANCE" | "CUSTOMER";
type AccountStatus = "active" | "inactive" | "locked";
type AccountType = "admin" | "customer";

type Account = {
  id: string;
  name: string;
  email: string;
  initials: string;
  role: AccountRole;
  type: AccountType;
  status: AccountStatus;
  lastLogin: string;
  lastLoginIP: string;
  color: "blue" | "amber" | "slate" | "emerald" | "red";
};

type AuditLog = {
  id: string;
  actor: string;
  action: string;
  target: string;
  ip: string;
  time: string;
};

type ApiAccount = Awaited<ReturnType<typeof adminAPI.getAccounts>>[number];
type ApiAuditLog = Awaited<ReturnType<typeof adminAPI.getAuditLogs>>[number];

const mapApiRole = (role: ApiAccount["role"]): AccountRole => (role === "ADMIN" ? "SUPER_ADMIN" : role);
const mapUiRole = (role: AccountRole) => (role === "SUPER_ADMIN" ? "ADMIN" : role);

const mapApiAccount = (account: ApiAccount): Account => ({
  id: String(account.id),
  name: account.name,
  email: account.email,
  initials: getInitials(account.name),
  role: mapApiRole(account.role),
  type: account.accountType === "CUSTOMER" ? "customer" : "admin",
  status: account.status.toLowerCase() as AccountStatus,
  lastLogin: account.updatedAt ? new Date(account.updatedAt).toLocaleString("vi-VN") : "Chua co",
  lastLoginIP: "API",
  color: account.accountType === "CUSTOMER" ? "emerald" : account.status === "LOCKED" ? "red" : "blue"
});

const mapApiAuditLog = (log: ApiAuditLog): AuditLog => ({
  id: String(log.id),
  actor: log.actor,
  action: log.action,
  target: log.target,
  ip: log.ipAddress ?? "system",
  time: new Date(log.createdAt).toLocaleString("vi-VN")
});

const initialAccounts: Account[] = [
  {
    id: "admin-1",
    name: "Nguyễn Minh Anh",
    email: "minhanh@kinetic.vn",
    initials: "MA",
    role: "SUPER_ADMIN",
    type: "admin",
    status: "active",
    lastLogin: "2 phút trước",
    lastLoginIP: "192.168.1.45",
    color: "blue"
  },
  {
    id: "admin-2",
    name: "Trần Quốc Huy",
    email: "huy.ops@kinetic.vn",
    initials: "QH",
    role: "OPERATOR",
    type: "admin",
    status: "active",
    lastLogin: "09:12 - 02/06/2026",
    lastLoginIP: "104.22.1.8",
    color: "amber"
  },
  {
    id: "admin-3",
    name: "Lê Thảo Vy",
    email: "support@kinetic.vn",
    initials: "TV",
    role: "SUPPORT",
    type: "admin",
    status: "inactive",
    lastLogin: "30/05/2026",
    lastLoginIP: "172.58.211.9",
    color: "slate"
  },
  {
    id: "user-1",
    name: "Phạm Gia Bảo",
    email: "giabao@example.com",
    initials: "GB",
    role: "CUSTOMER",
    type: "customer",
    status: "active",
    lastLogin: "15 phút trước",
    lastLoginIP: "10.2.16.88",
    color: "emerald"
  },
  {
    id: "user-2",
    name: "Hoàng Linh Chi",
    email: "linhchi@example.com",
    initials: "LC",
    role: "CUSTOMER",
    type: "customer",
    status: "locked",
    lastLogin: "28/05/2026",
    lastLoginIP: "10.2.16.92",
    color: "red"
  }
];

const initialAuditLogs: AuditLog[] = [
  {
    id: "log-1",
    actor: "Nguyễn Minh Anh",
    action: "cập nhật quyền",
    target: "Trần Quốc Huy thành Operator",
    ip: "192.168.1.45",
    time: "Vừa xong"
  },
  {
    id: "log-2",
    actor: "Trần Quốc Huy",
    action: "khóa tài khoản người dùng",
    target: "Hoàng Linh Chi",
    ip: "104.22.1.8",
    time: "18 phút trước"
  },
  {
    id: "log-3",
    actor: "Lê Thảo Vy",
    action: "xem lịch sử thuê xe",
    target: "Phạm Gia Bảo",
    ip: "172.58.211.9",
    time: "1 giờ trước"
  }
];

const roleLabel: Record<AccountRole, string> = {
  SUPER_ADMIN: "Super admin",
  OPERATOR: "Operator",
  SUPPORT: "Support",
  MAINTENANCE: "Maintenance",
  CUSTOMER: "Người dùng"
};

const statusLabel: Record<AccountStatus, string> = {
  active: "Hoạt động",
  inactive: "Tạm ngưng",
  locked: "Đã khóa"
};

const initialsColor = {
  blue: "bg-blue-100 text-blue-700",
  amber: "bg-amber-100 text-amber-800",
  slate: "bg-slate-100 text-slate-700",
  emerald: "bg-emerald-100 text-emerald-700",
  red: "bg-red-100 text-red-700"
};

const permissions = [
  {
    role: "SUPER_ADMIN",
    title: "Super admin",
    description: "Toàn quyền quản trị hệ thống, giá, khuyến mãi, tài khoản và nhật ký.",
    tags: ["PRICING_WRITE", "PROMO_WRITE", "ADMIN_MANAGE", "AUDIT_VIEW"]
  },
  {
    role: "OPERATOR",
    title: "Operator",
    description: "Vận hành hằng ngày: xem người dùng, xử lý chuyến thuê, cập nhật campaign.",
    tags: ["TRIP_MANAGE", "PROMO_WRITE", "USER_SUPPORT"]
  },
  {
    role: "SUPPORT",
    title: "Support",
    description: "Hỗ trợ khách hàng, xem hồ sơ và lịch sử thuê nhưng không sửa biểu phí.",
    tags: ["USER_READ", "TICKET_REPLY", "PAYMENT_READ"]
  },
  {
    role: "MAINTENANCE",
    title: "Maintenance",
    description: "Nhân sự bảo trì xem xe, báo lỗi và phiếu sửa chữa liên quan.",
    tags: ["BIKE_READ", "MAINTENANCE_WRITE"]
  }
];

const getInitials = (name: string) =>
  name
    .split(" ")
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

const AdminAccountsAccessPage = () => {
  const [accounts, setAccounts] = useState(initialAccounts);
  const [auditLogs, setAuditLogs] = useState(initialAuditLogs);
  const [activeTab, setActiveTab] = useState<"ACCOUNTS" | "ROLES" | "AUDIT">("ACCOUNTS");
  const [accountFilter, setAccountFilter] = useState<"all" | AccountType>("all");
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [menuAccountId, setMenuAccountId] = useState<string | null>(null);
  const [lastSyncTime, setLastSyncTime] = useState("14:02:45");
  const [isSyncing, setIsSyncing] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const [newAccount, setNewAccount] = useState({
    name: "",
    email: "",
    type: "admin" as AccountType,
    role: "SUPPORT" as AccountRole,
    status: "active" as AccountStatus
  });

  useEffect(() => {
    let cancelled = false;

    const loadAdminAccounts = async () => {
      try {
        const [apiAccounts, apiLogs] = await Promise.all([adminAPI.getAccounts(), adminAPI.getAuditLogs()]);
        if (cancelled) return;
        setAccounts(apiAccounts.map(mapApiAccount));
        setAuditLogs(apiLogs.map(mapApiAuditLog));
        setApiError(null);
      } catch (error) {
        if (!cancelled) {
          setApiError(error instanceof Error ? error.message : "Khong the tai du lieu tai khoan tu API.");
        }
      }
    };

    loadAdminAccounts();

    return () => {
      cancelled = true;
    };
  }, []);

  const metrics = useMemo(
    () => ({
      total: accounts.length,
      admins: accounts.filter((account) => account.type === "admin").length,
      customers: accounts.filter((account) => account.type === "customer").length,
      locked: accounts.filter((account) => account.status === "locked").length
    }),
    [accounts]
  );

  const filteredAccounts = accounts.filter((account) => accountFilter === "all" || account.type === accountFilter);

  const addAudit = (actor: string, action: string, target: string) => {
    setAuditLogs((current) => [
      {
        id: `log-${Date.now()}`,
        actor,
        action,
        target,
        ip: "192.168.1.45",
        time: "Vừa xong"
      },
      ...current
    ]);
  };

  const handleSync = () => {
    setIsSyncing(true);
    window.setTimeout(() => {
      const now = new Date();
      setLastSyncTime(
        `${now.getHours().toString().padStart(2, "0")}:${now.getMinutes().toString().padStart(2, "0")}:${now
          .getSeconds()
          .toString()
          .padStart(2, "0")}`
      );
      setIsSyncing(false);
    }, 700);
  };

  const handleAddAccount = (event: FormEvent) => {
    event.preventDefault();
    if (!newAccount.name.trim() || !newAccount.email.trim()) return;

    const role = newAccount.type === "customer" ? "CUSTOMER" : newAccount.role;

    setAccounts((current) => [
      {
        id: `${newAccount.type}-${Date.now()}`,
        name: newAccount.name.trim(),
        email: newAccount.email.trim(),
        initials: getInitials(newAccount.name),
        role,
        type: newAccount.type,
        status: newAccount.status,
        lastLogin: "Vừa tạo",
        lastLoginIP: "192.168.1.99",
        color: newAccount.type === "customer" ? "emerald" : "blue"
      },
      ...current
    ]);
    addAudit("Super Admin", "tạo tài khoản", newAccount.name.trim());
    adminAPI
      .createAccount({
        name: newAccount.name.trim(),
        email: newAccount.email.trim(),
        role: mapUiRole(role),
        status: newAccount.status.toUpperCase() as "ACTIVE" | "INACTIVE" | "LOCKED",
        verified: true
      })
      .then(async () => {
        const [apiAccounts, apiLogs] = await Promise.all([adminAPI.getAccounts(), adminAPI.getAuditLogs()]);
        setAccounts(apiAccounts.map(mapApiAccount));
        setAuditLogs(apiLogs.map(mapApiAuditLog));
        setApiError(null);
      })
      .catch((error) => setApiError(error instanceof Error ? error.message : "Khong the tao tai khoan."));
    setNewAccount({ name: "", email: "", type: "admin", role: "SUPPORT", status: "active" });
    setIsAddOpen(false);
  };

  const handleRoleChange = (account: Account, nextRole: AccountRole) => {
    setAccounts((current) => current.map((item) => (item.id === account.id ? { ...item, role: nextRole } : item)));
    adminAPI
      .updateAccountRole(account.id, mapUiRole(nextRole))
      .then((saved) => {
        setAccounts((current) => current.map((item) => (item.id === String(saved.id) ? mapApiAccount(saved) : item)));
        setApiError(null);
      })
      .catch((error) => setApiError(error instanceof Error ? error.message : "Khong the cap nhat quyen."));
    addAudit("Super Admin", "cập nhật quyền", `${account.name} thành ${roleLabel[nextRole]}`);
    setMenuAccountId(null);
  };

  const handleStatusToggle = (account: Account) => {
    const nextStatus: AccountStatus = account.status === "locked" || account.status === "inactive" ? "active" : "locked";
    setAccounts((current) => current.map((item) => (item.id === account.id ? { ...item, status: nextStatus } : item)));
    adminAPI
      .updateAccountStatus(account.id, nextStatus.toUpperCase() as "ACTIVE" | "INACTIVE" | "LOCKED")
      .then((saved) => {
        setAccounts((current) => current.map((item) => (item.id === String(saved.id) ? mapApiAccount(saved) : item)));
        setApiError(null);
      })
      .catch((error) => setApiError(error instanceof Error ? error.message : "Khong the cap nhat trang thai."));
    addAudit("Super Admin", nextStatus === "active" ? "mở khóa tài khoản" : "khóa tài khoản", account.name);
    setMenuAccountId(null);
  };

  const handleDeleteAccount = (account: Account) => {
    setAccounts((current) => current.filter((item) => item.id !== account.id));
    adminAPI
      .deleteAccount(account.id)
      .then(() => setApiError(null))
      .catch((error) => setApiError(error instanceof Error ? error.message : "Khong the xoa tai khoan."));
    addAudit("Super Admin", "xóa tài khoản", account.name);
    setMenuAccountId(null);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <h2 className="font-display text-3xl font-bold tracking-tight text-slate-950">Quản lý tài khoản và phân quyền</h2>
          <p className="mt-1 text-sm text-slate-500">
            Gộp quản lý admin, nhân viên vận hành và người dùng cuối vào cùng một màn hình kiểm soát.
          </p>
        </div>
        <button
          onClick={() => setIsAddOpen(true)}
          className="inline-flex items-center justify-center gap-2 rounded-lg bg-[#0051c3] px-5 py-2.5 text-xs font-bold uppercase tracking-wider text-white shadow-md shadow-blue-200 transition hover:bg-blue-800"
        >
          <UserPlus className="h-4 w-4" />
          Thêm tài khoản
        </button>
      </div>

      {apiError && (
        <div className="rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-800">
          API admin chua san sang hoac tai khoan hien tai chua co quyen: {apiError}
        </div>
      )}

      <div className="flex gap-8 border-b border-slate-200">
        {[
          ["ACCOUNTS", "Danh sách tài khoản"],
          ["ROLES", "Phân quyền"],
          ["AUDIT", "Nhật ký hoạt động"]
        ].map(([id, label]) => (
          <button
            key={id}
            onClick={() => setActiveTab(id as "ACCOUNTS" | "ROLES" | "AUDIT")}
            className={`border-b-2 pb-3 text-sm font-bold transition ${
              activeTab === id ? "border-[#0051c3] text-[#0051c3]" : "border-transparent text-slate-400 hover:text-slate-700"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {activeTab === "ACCOUNTS" && (
        <>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-4">
            <div className="rounded-xl border border-slate-100 bg-white p-5 shadow-[0_4px_12px_rgba(15,23,42,0.02)]">
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Tổng tài khoản</p>
              <p className="mt-2 font-display text-3xl font-bold text-slate-950">{metrics.total}</p>
            </div>
            <div className="rounded-xl border border-slate-100 bg-white p-5 shadow-[0_4px_12px_rgba(15,23,42,0.02)]">
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Admin/Nhân viên</p>
              <p className="mt-2 font-display text-3xl font-bold text-slate-950">{metrics.admins}</p>
            </div>
            <div className="rounded-xl border border-slate-100 bg-white p-5 shadow-[0_4px_12px_rgba(15,23,42,0.02)]">
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Người dùng</p>
              <p className="mt-2 font-display text-3xl font-bold text-slate-950">{metrics.customers}</p>
            </div>
            <div className="rounded-xl border border-slate-100 bg-[#0051c3] p-5 text-white shadow-lg shadow-blue-200">
              <p className="text-xs font-bold uppercase tracking-wider text-blue-100">Cần xử lý</p>
              <p className="mt-2 font-display text-3xl font-bold">{metrics.locked}</p>
            </div>
          </div>

          <div className="overflow-hidden rounded-xl border border-slate-100 bg-white shadow-[0_4px_12px_rgba(15,23,42,0.02)]">
            <div className="flex flex-col justify-between gap-3 border-b border-slate-100 bg-slate-50/50 px-6 py-4 sm:flex-row sm:items-center">
              <h3 className="font-display text-base font-bold text-slate-800">Tài khoản hệ thống</h3>
              <div className="flex items-center gap-2">
                <select
                  value={accountFilter}
                  onChange={(event) => setAccountFilter(event.target.value as "all" | AccountType)}
                  className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 outline-none"
                >
                  <option value="all">Tất cả</option>
                  <option value="admin">Admin/Nhân viên</option>
                  <option value="customer">Người dùng</option>
                </select>
                <button className="rounded-lg border border-slate-200 bg-white p-2 text-slate-400 hover:text-[#0051c3]" title="Lọc nâng cao">
                  <ListFilter className="h-4 w-4" />
                </button>
                <button className="rounded-lg border border-slate-200 bg-white p-2 text-slate-400 hover:text-[#0051c3]" title="Xuất CSV">
                  <Download className="h-4 w-4" />
                </button>
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50 text-[11px] uppercase tracking-wider text-slate-400">
                    <th className="px-6 py-4">Tài khoản</th>
                    <th className="px-6 py-4">Loại</th>
                    <th className="px-6 py-4">Quyền</th>
                    <th className="px-6 py-4">Trạng thái</th>
                    <th className="px-6 py-4">Lần đăng nhập cuối</th>
                    <th className="px-6 py-4 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {filteredAccounts.map((account) => (
                    <tr key={account.id} className="transition hover:bg-slate-50/70">
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <div className={`flex h-9 w-9 items-center justify-center rounded-lg text-xs font-bold ${initialsColor[account.color]}`}>
                            {account.initials}
                          </div>
                          <div>
                            <p className="font-bold text-slate-900">{account.name}</p>
                            <p className="mt-0.5 text-xs text-slate-400">{account.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-bold uppercase text-slate-600">
                          {account.type === "admin" ? <ShieldCheck className="h-3 w-3" /> : <UserRound className="h-3 w-3" />}
                          {account.type === "admin" ? "Admin" : "User"}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <select
                          value={account.role}
                          disabled={account.type === "customer"}
                          onChange={(event) => handleRoleChange(account, event.target.value as AccountRole)}
                          className="rounded-lg border border-blue-100 bg-blue-50 px-2.5 py-1 text-xs font-bold text-[#0051c3] outline-none disabled:cursor-not-allowed disabled:border-slate-100 disabled:bg-slate-50 disabled:text-slate-400"
                        >
                          <option value="SUPER_ADMIN">Super admin</option>
                          <option value="OPERATOR">Operator</option>
                          <option value="SUPPORT">Support</option>
                          <option value="MAINTENANCE">Maintenance</option>
                          <option value="CUSTOMER">Người dùng</option>
                        </select>
                      </td>
                      <td className="px-6 py-4">
                        <span
                          className={`inline-flex items-center gap-2 text-xs font-semibold ${
                            account.status === "active"
                              ? "text-emerald-700"
                              : account.status === "locked"
                                ? "text-red-600"
                                : "text-slate-400"
                          }`}
                        >
                          <span
                            className={`h-2 w-2 rounded-full ${
                              account.status === "active"
                                ? "bg-emerald-500"
                                : account.status === "locked"
                                  ? "bg-red-500"
                                  : "bg-slate-300"
                            }`}
                          />
                          {statusLabel[account.status]}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <p className="text-xs font-semibold text-slate-700">{account.lastLogin}</p>
                        <p className="mt-1 font-mono text-[10px] text-slate-400">IP: {account.lastLoginIP}</p>
                      </td>
                      <td className="relative px-6 py-4 text-right">
                        <button
                          onClick={() => setMenuAccountId(menuAccountId === account.id ? null : account.id)}
                          className="rounded-lg p-1.5 text-slate-300 transition hover:bg-slate-100 hover:text-[#0051c3]"
                        >
                          <MoreVertical className="h-4 w-4" />
                        </button>
                        {menuAccountId === account.id && (
                          <div className="absolute right-6 top-12 z-20 w-44 rounded-lg border border-slate-100 bg-white py-1.5 text-left shadow-lg">
                            <button
                              onClick={() => handleStatusToggle(account)}
                              className="flex w-full items-center gap-2 px-4 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50"
                            >
                              <UserX className="h-3.5 w-3.5 text-[#0051c3]" />
                              {account.status === "active" ? "Khóa tài khoản" : "Mở tài khoản"}
                            </button>
                            <button
                              onClick={() => handleDeleteAccount(account)}
                              className="flex w-full items-center gap-2 px-4 py-2 text-xs font-medium text-red-600 hover:bg-red-50"
                            >
                              <X className="h-3.5 w-3.5" />
                              Xóa tài khoản
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex items-center justify-between border-t border-slate-100 px-6 py-4">
              <p className="text-xs font-medium text-slate-400">
                Hiển thị <span className="font-bold text-slate-700">{filteredAccounts.length}</span> tài khoản
              </p>
              <button
                onClick={handleSync}
                className={`inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-bold text-slate-600 ${
                  isSyncing ? "text-[#0051c3]" : ""
                }`}
              >
                <RefreshCw className={`h-4 w-4 ${isSyncing ? "animate-spin" : ""}`} />
                Sync {lastSyncTime}
              </button>
            </div>
          </div>
        </>
      )}

      {activeTab === "ROLES" && (
        <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-[0_4px_12px_rgba(15,23,42,0.02)]">
          <h3 className="font-display text-lg font-bold text-slate-900">Ma trận phân quyền</h3>
          <p className="mt-1 text-xs text-slate-400">Định nghĩa vai trò nên dùng cho admin, vận hành, hỗ trợ và bảo trì.</p>

          <div className="mt-6 space-y-4">
            {permissions.map((permission) => (
              <div key={permission.role} className="flex flex-col justify-between gap-4 rounded-xl border border-slate-100 p-5 md:flex-row md:items-center">
                <div>
                  <div className="flex items-center gap-2">
                    <ShieldCheck className="h-5 w-5 text-[#0051c3]" />
                    <h4 className="font-display font-bold text-slate-950">{permission.title}</h4>
                  </div>
                  <p className="mt-1 max-w-2xl text-xs leading-relaxed text-slate-500">{permission.description}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {permission.tags.map((tag) => (
                    <span key={tag} className="rounded-lg bg-blue-50 px-2.5 py-1 text-[10px] font-bold uppercase text-[#0051c3]">
                      {tag}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeTab === "AUDIT" && (
        <div className="rounded-xl border border-slate-100 bg-white p-6 shadow-[0_4px_12px_rgba(15,23,42,0.02)]">
          <div className="mb-5 flex items-center justify-between border-b border-slate-100 pb-4">
            <div>
              <h3 className="font-display text-lg font-bold text-slate-900">Nhật ký hoạt động</h3>
              <p className="mt-1 text-xs text-slate-400">Ghi lại ai đã sửa gì, thời điểm nào và tác động tới tài khoản nào.</p>
            </div>
            <History className="h-5 w-5 text-[#0051c3]" />
          </div>
          <div className="max-h-[430px] divide-y divide-slate-100 overflow-y-auto pr-1">
            {auditLogs.map((log) => (
              <div key={log.id} className="flex items-start justify-between gap-4 py-3 text-xs">
                <div>
                  <p className="font-semibold text-slate-800">
                    <span className="text-[#0051c3]">{log.actor}</span> {log.action}{" "}
                    <span className="font-bold text-slate-950">{log.target}</span>
                  </p>
                  <p className="mt-1 font-mono text-[10px] text-slate-400">IP: {log.ip}</p>
                </div>
                <p className="flex shrink-0 items-center gap-1 whitespace-nowrap font-medium text-slate-400">
                  <Clock className="h-3 w-3" />
                  {log.time}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      {isAddOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md overflow-hidden rounded-xl border border-slate-100 bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-6 py-4">
              <h3 className="font-display text-lg font-bold text-slate-900">Thêm tài khoản</h3>
              <button onClick={() => setIsAddOpen(false)} className="text-slate-400 hover:text-slate-700">
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleAddAccount} className="space-y-4 p-6">
              <div>
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Họ tên</label>
                <input
                  required
                  value={newAccount.name}
                  onChange={(event) => setNewAccount((current) => ({ ...current, name: event.target.value }))}
                  className="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-[#0051c3]/20"
                />
              </div>
              <div>
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Email</label>
                <input
                  type="email"
                  required
                  value={newAccount.email}
                  onChange={(event) => setNewAccount((current) => ({ ...current, email: event.target.value }))}
                  className="mt-1.5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-[#0051c3]/20"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Loại</label>
                  <select
                    value={newAccount.type}
                    onChange={(event) =>
                      setNewAccount((current) => ({
                        ...current,
                        type: event.target.value as AccountType,
                        role: event.target.value === "customer" ? "CUSTOMER" : "SUPPORT"
                      }))
                    }
                    className="mt-1.5 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm outline-none"
                  >
                    <option value="admin">Admin/Nhân viên</option>
                    <option value="customer">Người dùng</option>
                  </select>
                </div>
                <div>
                  <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Trạng thái</label>
                  <select
                    value={newAccount.status}
                    onChange={(event) => setNewAccount((current) => ({ ...current, status: event.target.value as AccountStatus }))}
                    className="mt-1.5 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm outline-none"
                  >
                    <option value="active">Hoạt động</option>
                    <option value="inactive">Tạm ngưng</option>
                    <option value="locked">Đã khóa</option>
                  </select>
                </div>
              </div>
              {newAccount.type === "admin" && (
                <div>
                  <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Vai trò</label>
                  <select
                    value={newAccount.role}
                    onChange={(event) => setNewAccount((current) => ({ ...current, role: event.target.value as AccountRole }))}
                    className="mt-1.5 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm outline-none"
                  >
                    <option value="SUPER_ADMIN">Super admin</option>
                    <option value="OPERATOR">Operator</option>
                    <option value="SUPPORT">Support</option>
                    <option value="MAINTENANCE">Maintenance</option>
                  </select>
                </div>
              )}
              <div className="rounded-xl border border-blue-100 bg-blue-50/50 p-3 text-xs text-slate-600">
                <CheckCircle2 className="mr-1 inline h-4 w-4 text-[#0051c3]" />
                Tài khoản mới sẽ xuất hiện ngay trong bảng và sinh một dòng audit log.
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <button type="button" onClick={() => setIsAddOpen(false)} className="px-4 py-2 text-xs font-bold uppercase text-slate-500">
                  Hủy
                </button>
                <button type="submit" className="rounded-lg bg-[#0051c3] px-5 py-2 text-xs font-bold uppercase tracking-wider text-white">
                  Tạo tài khoản
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="flex items-center gap-2 text-xs font-semibold text-slate-400">
        <Users className="h-4 w-4" />
        Người dùng cuối được để role CUSTOMER và không chỉnh quyền như nhân viên nội bộ.
      </div>
    </div>
  );
};

export default AdminAccountsAccessPage;
