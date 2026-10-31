import type { ChangeEvent, FormEvent } from "react";
import { useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { ArrowRight, Eye, EyeOff, Lock, Mail, Zap } from "lucide-react";
import { useAuth } from "@ebike/shared-code/hooks";

type LoginFormData = {
  usernameOrEmail: string;
  password: string;
  rememberMe: boolean;
};

const LoginPageApi = () => {
  const navigate = useNavigate();
  const { login, isLoading } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [formData, setFormData] = useState<LoginFormData>({
    usernameOrEmail: "",
    password: "",
    rememberMe: false
  });

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value
    }));
    setError("");
  };

  const validateForm = () => {
    if (!formData.usernameOrEmail.trim()) {
      setError("Tên đăng nhập hoặc email là bắt buộc");
      return false;
    }
    if (!formData.password) {
      setError("Mật khẩu là bắt buộc");
      return false;
    }
    if (formData.password.length < 6) {
      setError("Mật khẩu phải có ít nhất 6 ký tự");
      return false;
    }
    return true;
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    if (!validateForm()) return;

    try {
      const result = await login({
        usernameOrEmail: formData.usernameOrEmail.trim(),
        password: formData.password
      }).unwrap();

      navigate(
        result.roles.includes("ADMIN")
          ? "/admin"
          : result.roles.includes("MANAGER")
            ? "/manager"
            : "/customer/profile"
      );
    } catch (loginError) {
      const message =
        loginError instanceof Error ? loginError.message : "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.";
      setError(message);
    }
  };

  return (
    <div className="flex min-h-screen bg-surface">
      <aside className="relative hidden w-1/2 overflow-hidden bg-primary lg:flex lg:items-end">
        <img
          src="https://images.unsplash.com/photo-1593941707882-a5bba14938c7?auto=format&fit=crop&q=80&w=2000"
          alt="Xe điện KINETIC"
          className="absolute inset-0 h-full w-full object-cover opacity-60 mix-blend-overlay"
          referrerPolicy="no-referrer"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-primary/95 via-primary/50 to-transparent" />
        <div className="relative z-10 p-12 text-white">
          <div className="mb-8 flex items-center gap-3">
            <Zap className="h-8 w-8 fill-white" />
            <span className="font-headline text-2xl font-bold tracking-tight">KINETIC Electric</span>
          </div>

          <h1 className="max-w-xl font-headline text-5xl font-bold leading-tight tracking-tight">
            Quản lý hành trình xe điện của bạn.
          </h1>
          <p className="mt-6 max-w-md text-lg leading-8 text-white/80">
            Đăng nhập để theo dõi đơn hàng, cập nhật hồ sơ và tiếp tục trải nghiệm mua xe điện tại KINETIC.
          </p>

          <div className="mt-10 flex gap-12">
            <div>
              <span className="mono-label text-white/60">SHOWROOM</span>
              <p className="mt-2 font-headline text-3xl font-bold">TP.HCM</p>
            </div>
            <div>
              <span className="mono-label text-white/60">HỖ TRỢ</span>
              <p className="mt-2 font-headline text-3xl font-bold">24/7</p>
            </div>
          </div>
        </div>
      </aside>

      <main className="flex w-full flex-col items-center justify-center px-6 py-10 md:px-12 lg:w-1/2 lg:px-20">
        <div className="w-full max-w-md">
          <div className="mb-10">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full bg-primary-fixed px-4 py-2 text-sm font-bold text-primary">
              <Zap className="h-4 w-4 fill-primary" />
              KINETIC Account
            </div>
            <h2 className="font-headline text-4xl font-bold tracking-tight text-foreground">Chào mừng quay lại</h2>
            <p className="mt-3 text-base leading-7 text-muted-foreground">
              Nhập thông tin tài khoản để truy cập khu vực khách hàng hoặc dashboard quản trị.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-6">
            {error ? (
              <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                {error}
              </div>
            ) : null}

            <div>
              <label htmlFor="usernameOrEmail" className="mono-label mb-2 block text-muted-foreground">
                Tên đăng nhập hoặc email
              </label>
              <div className="relative">
                <Mail className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-outline" />
                <input
                  type="text"
                  id="usernameOrEmail"
                  name="usernameOrEmail"
                  value={formData.usernameOrEmail}
                  onChange={handleChange}
                  placeholder="johndoe hoặc john@example.com"
                  className="input-base py-4 pl-12"
                  disabled={isLoading}
                />
              </div>
            </div>

            <div>
              <div className="mb-2 flex items-end justify-between gap-4">
                <label htmlFor="password" className="mono-label block text-muted-foreground">
                  Mật khẩu
                </label>
                <button type="button" className="text-xs font-bold text-primary transition hover:text-primary-container">
                  Quên mật khẩu?
                </button>
              </div>
              <div className="relative">
                <Lock className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-outline" />
                <input
                  type={showPassword ? "text" : "password"}
                  id="password"
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  placeholder="Nhập mật khẩu"
                  className="input-base py-4 pl-12 pr-12"
                  disabled={isLoading}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-outline transition hover:text-foreground"
                  disabled={isLoading}
                  aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <input
                type="checkbox"
                id="rememberMe"
                name="rememberMe"
                checked={formData.rememberMe}
                onChange={handleChange}
                className="h-4 w-4 cursor-pointer rounded border-outline-variant text-primary focus:ring-primary"
                disabled={isLoading}
              />
              <label htmlFor="rememberMe" className="cursor-pointer text-sm font-medium text-muted-foreground">
                Ghi nhớ phiên đăng nhập trên thiết bị này
              </label>
            </div>

            <button type="submit" disabled={isLoading} className="btn-primary w-full py-4 group disabled:cursor-not-allowed disabled:opacity-60">
              {isLoading ? (
                <>
                  <span className="h-5 w-5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Đang đăng nhập...
                </>
              ) : (
                <>
                  Đăng nhập
                  <ArrowRight className="h-5 w-5 transition-transform group-hover:translate-x-1" />
                </>
              )}
            </button>
          </form>

          <div className="mt-8 text-center">
            <p className="text-sm text-muted-foreground">
              Chưa có tài khoản?{" "}
              <NavLink to="/signup" className="font-bold text-primary transition hover:text-primary-container">
                Tạo tài khoản
              </NavLink>
            </p>
          </div>
        </div>

        <footer className="mt-auto pt-16 text-center text-xs text-muted-foreground/70">
          © 2026 KINETIC Electric. Secure access for customers and operations.
        </footer>
      </main>
    </div>
  );
};

export default LoginPageApi;
