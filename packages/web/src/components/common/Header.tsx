import { Menu, User } from "lucide-react";
import { useEffect, useState } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "@ebike/shared-code/hooks";
import NotificationBell from "./NotificationBell";

const navItems = [
  { to: "/products", label: "Mẫu xe" },
  { to: "/support", label: "Hỗ trợ" },
  { to: "/chatbot", label: "Tư vấn" },
  { to: "/favorites", label: "Đã lưu" }
];

const adminRoles = new Set(["ADMIN", "OPERATOR", "SUPPORT", "MAINTENANCE"]);

const Header = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, user } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const isManager = user?.roles.includes("MANAGER");
  const canAccessAdmin = user?.roles.some((role) => adminRoles.has(role));
  const isNotificationsPage = location.pathname === "/customer/notifications";
  const visibleNavItems = [
    ...navItems.filter((item) => isAuthenticated || item.to !== "/favorites"),
    ...(canAccessAdmin ? [{ to: "/admin", label: "Admin", end: false }] : []),
    ...(isManager ? [{ to: "/manager", label: "Manager", end: false }] : [])
  ];

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  return (
    <header className="glass-effect fixed left-0 right-0 top-0 z-50">
      <nav className="mx-auto flex max-w-screen-2xl items-center justify-between px-6 py-5 md:px-12">
        <div className="flex items-center gap-12">
          <NavLink to="/" className="font-headline text-2xl font-bold uppercase tracking-tight text-foreground">
            KINETIC
          </NavLink>
          <div className="hidden items-center gap-8 md:flex">
            <NavLink
              to="/"
              end
              className={({ isActive }) =>
                `font-headline tracking-tight transition-all duration-300 hover:text-primary ${
                  isActive ? "border-b-2 border-primary pb-1 text-primary" : "text-foreground/70"
                }`
              }
            >
              Trang chủ
            </NavLink>
            {visibleNavItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={"end" in item ? item.end : undefined}
                className={({ isActive }) =>
                  `font-headline tracking-tight transition-all duration-300 hover:text-primary ${
                    isActive ? "border-b-2 border-primary pb-1 text-primary" : "text-foreground/70"
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="hidden items-center gap-4 lg:flex">
            {isNotificationsPage ? null : <NotificationBell enabled={isAuthenticated} />}
            <button
              onClick={() => navigate(isAuthenticated ? "/customer/profile" : "/auth")}
              className="text-foreground/70 transition-colors hover:text-primary"
              aria-label="Tài khoản"
            >
              <User size={18} />
            </button>
          </div>
          <NavLink to={isAuthenticated ? "/products" : "/auth"} className="btn-primary hidden text-sm sm:inline-flex">
            {isAuthenticated ? "Mua Ngay " : "Đăng nhập"}
          </NavLink>
          <button
            onClick={() => setMobileOpen((value) => !value)}
            className="text-foreground md:hidden"
            aria-label="Mở menu điều hướng"
          >
            <Menu size={24} />
          </button>
        </div>
      </nav>

      {mobileOpen && (
        <div className="border-t border-outline-variant/20 bg-background px-6 py-6 md:hidden">
          <div className="flex flex-col gap-4">
            <NavLink
              to="/"
              end
              className="font-headline text-foreground/70 hover:text-primary"
            >
              Trang chủ
            </NavLink>
            {visibleNavItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={"end" in item ? item.end : undefined}
                className="font-headline text-foreground/70 hover:text-primary"
              >
                {item.label}
              </NavLink>
            ))}
            <NavLink to={isAuthenticated ? "/products" : "/auth"} className="btn-primary mt-2 w-full">
              {isAuthenticated ? "Mua Ngay " : "Đăng nhập"}
            </NavLink>
          </div>
        </div>
      )}
    </header>
  );
};

export default Header;
