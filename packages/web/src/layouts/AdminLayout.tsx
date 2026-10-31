import { Navigate, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "@ebike/shared-code/hooks";
import {
  BadgePercent,
  Bell,
  Home,
  LayoutDashboard,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Users
} from "lucide-react";

const adminNavItems = [
  { to: "/admin", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/admin/pricing", label: "Giá & khuyến mãi", icon: BadgePercent },
  { to: "/admin/accounts", label: "Tài khoản", icon: Users }
];

const adminRoles = new Set(["ADMIN", "OPERATOR", "SUPPORT", "MAINTENANCE"]);

const AdminLayout = () => {
  const { isBootstrapping, isAuthenticated, user } = useAuth();
  const canAccessAdmin = user?.roles.some((role) => adminRoles.has(role));

  if (isBootstrapping) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#faf8ff] text-sm font-semibold text-slate-500">
        Dang tai phien lam viec...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/auth" replace />;
  }

  if (!canAccessAdmin) {
    return <Navigate to="/customer/profile" replace />;
  }

  return (
  <section className="min-h-screen bg-[#faf8ff] text-slate-950">
    <aside className="fixed left-0 top-0 z-40 hidden h-full w-[260px] flex-col border-r border-slate-100 bg-white py-8 shadow-[0_4px_18px_rgba(15,23,42,0.04)] lg:flex">
      <div className="px-8">
        <h1 className="font-display text-2xl font-bold tracking-tight text-[#0051c3]">Kinetic Admin</h1>
        <p className="mt-1 text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">E-bike console</p>
        <NavLink
          to="/"
          className="mt-5 flex items-center gap-3 rounded-lg border border-slate-100 px-4 py-3 text-sm font-semibold text-slate-600 transition hover:border-[#0051c3]/20 hover:bg-blue-50/50 hover:text-[#0051c3]"
        >
          <Home className="h-4 w-4" />
          <span>Trang chu</span>
        </NavLink>
      </div>

      <nav className="mt-10 flex-1 space-y-1 px-4">
        {adminNavItems.map((item) => {
          const Icon = item.icon;

          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex items-center gap-4 rounded-lg border-l-4 px-4 py-3 text-sm font-semibold transition ${
                  isActive
                    ? "border-[#0051c3] bg-slate-50 text-[#0051c3]"
                    : "border-transparent text-slate-500 hover:bg-slate-50 hover:text-slate-950"
                }`
              }
            >
              <Icon className="h-5 w-5" />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </nav>

      <div className="px-4">
        <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[#0051c3] font-display text-sm font-bold text-white">
            SA
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-bold text-slate-900">Super Admin</p>
            <p className="flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              <ShieldCheck className="h-3 w-3 text-[#0051c3]" />
              Secure mode
            </p>
          </div>
        </div>
      </div>
    </aside>

    <div className="min-h-screen lg:ml-[260px]">
      <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-100 bg-white/95 px-4 backdrop-blur-md sm:px-8">
        <div className="relative w-full max-w-sm">
          <Search className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            placeholder="Tìm giá, voucher, tài khoản..."
            className="w-full rounded-lg border-0 bg-slate-50 py-2 pl-10 pr-4 text-sm font-medium text-slate-700 outline-none transition focus:bg-white focus:ring-2 focus:ring-[#0051c3]/15"
          />
        </div>

        <div className="ml-4 flex items-center gap-3">
          <NavLink
            to="/"
            className="hidden items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold text-slate-500 transition hover:bg-slate-50 hover:text-[#0051c3] sm:flex"
            title="Ve trang chu"
          >
            <Home className="h-4 w-4" />
            <span>Trang chu</span>
          </NavLink>
          <button className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-50 hover:text-[#0051c3]" title="Bộ lọc nhanh">
            <SlidersHorizontal className="h-5 w-5" />
          </button>
          <button className="relative rounded-lg p-2 text-slate-500 transition hover:bg-slate-50 hover:text-[#0051c3]" title="Thông báo">
            <Bell className="h-5 w-5" />
            <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-red-600 ring-2 ring-white" />
          </button>
          <div className="hidden items-center gap-2 rounded-full border border-blue-100 bg-blue-50/60 px-3 py-1.5 text-[10px] font-bold uppercase tracking-wider text-[#0051c3] sm:flex">
            <ShieldCheck className="h-3.5 w-3.5" />
            Admin
          </div>
        </div>
      </header>

      <div className="border-b border-slate-100 bg-white px-4 py-3 lg:hidden">
        <nav className="flex gap-2 overflow-x-auto">
          <NavLink
            to="/"
            className="whitespace-nowrap rounded-lg bg-slate-50 px-3 py-2 text-sm font-semibold text-slate-600"
          >
            Trang chu
          </NavLink>
          {adminNavItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `whitespace-nowrap rounded-lg px-3 py-2 text-sm font-semibold ${
                  isActive ? "bg-[#0051c3] text-white" : "bg-slate-50 text-slate-600"
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </div>

      <main className="mx-auto w-full max-w-7xl p-4 sm:p-8">
        <Outlet />
      </main>
    </div>
  </section>
  );
};

export default AdminLayout;
