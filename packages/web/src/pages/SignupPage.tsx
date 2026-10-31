import type { ChangeEvent, FormEvent } from "react";
import { useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { ArrowRight, Eye, EyeOff, Info, Lock, Mail, ShieldCheck, User, Zap } from "lucide-react";
import { authAPI } from "@ebike/shared-code/api";
import { useAuth } from "@ebike/shared-code/hooks";

type SignupFormData = {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
  firstName: string;
  lastName: string;
};

const initialFormData: SignupFormData = {
  username: "",
  email: "",
  password: "",
  confirmPassword: "",
  firstName: "",
  lastName: ""
};

const getErrorMessage = (error: unknown) => {
  if (error instanceof Error) {
    return error.message;
  }
  return "Tạo tài khoản thất bại. Vui lòng kiểm tra lại thông tin.";
};

const SignupPage = () => {
  const navigate = useNavigate();
  const { login, isLoading } = useAuth();
  const [formData, setFormData] = useState<SignupFormData>(initialFormData);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setFormData((current) => ({ ...current, [name]: value }));
    setError("");
  };

  const validateForm = () => {
    if (!formData.username.trim() || !formData.email.trim() || !formData.password) {
      setError("Tên đăng nhập, email và mật khẩu là bắt buộc.");
      return false;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email.trim())) {
      setError("Email không hợp lệ.");
      return false;
    }
    if (formData.password.length < 6) {
      setError("Mật khẩu phải có ít nhất 6 ký tự.");
      return false;
    }
    if (formData.password !== formData.confirmPassword) {
      setError("Xác nhận mật khẩu không khớp.");
      return false;
    }
    return true;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);
    try {
      await authAPI.register({
        username: formData.username.trim(),
        email: formData.email.trim(),
        password: formData.password,
        firstName: formData.firstName.trim() || undefined,
        lastName: formData.lastName.trim() || undefined
      });

      await login({
        usernameOrEmail: formData.username.trim(),
        password: formData.password
      }).unwrap();

      navigate("/customer/profile");
    } catch (signupError) {
      setError(getErrorMessage(signupError));
    } finally {
      setIsSubmitting(false);
    }
  };

  const disabled = isSubmitting || isLoading;

  return (
    <div className="flex min-h-screen bg-surface">
      <aside className="relative hidden w-1/2 overflow-hidden bg-primary lg:flex lg:items-end">
        <img
          src="https://images.unsplash.com/photo-1473341304170-971dccb5ac1e?auto=format&fit=crop&q=80&w=2000"
          alt="Hạ tầng xe điện KINETIC"
          className="absolute inset-0 h-full w-full object-cover opacity-60 mix-blend-overlay"
          referrerPolicy="no-referrer"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-primary/95 via-primary/50 to-transparent" />
        <div className="relative z-10 p-12 text-white">
          <div className="mb-8 flex items-center gap-3">
            <Zap className="h-8 w-8 fill-white" />
            <span className="font-headline text-2xl font-bold tracking-tight">KINETIC Membership</span>
          </div>

          <p className="max-w-md text-lg leading-8 text-white/80">
            Tạo tài khoản để lưu thông tin mua xe, theo dõi đơn hàng và nhận hỗ trợ từ showroom KINETIC.
          </p>

          <div className="mt-12 rounded-xl border border-white/10 bg-primary-container/20 p-6 backdrop-blur-md">
            <ShieldCheck className="mb-5 h-12 w-12 text-white" />
            <h3 className="font-headline text-2xl font-bold">Bảo mật tài khoản</h3>
            <p className="mt-3 text-sm leading-6 text-white/70">
              Thông tin đăng nhập được dùng để bảo vệ lịch sử đơn hàng, thanh toán và hồ sơ khách hàng của bạn.
            </p>
          </div>
        </div>
      </aside>

      <main className="flex w-full flex-col items-center justify-center px-6 py-10 md:px-12 lg:w-1/2 lg:px-20">
        <div className="w-full max-w-md">
          <div className="mb-8">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full bg-primary-fixed px-4 py-2 text-sm font-bold text-primary">
              <User className="h-4 w-4" />
              New KINETIC Account
            </div>
            <h2 className="font-headline text-4xl font-bold tracking-tight text-foreground">Tạo tài khoản</h2>
            <p className="mt-3 text-base leading-7 text-muted-foreground">
              Nhập thông tin cá nhân để bắt đầu mua sắm và theo dõi đơn hàng xe điện.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            {error ? (
              <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
                {error}
              </div>
            ) : null}

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="firstName" className="mono-label mb-2 block text-muted-foreground">
                  Họ
                </label>
                <div className="relative">
                  <User className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-outline" />
                  <input
                    id="firstName"
                    name="firstName"
                    value={formData.firstName}
                    onChange={handleChange}
                    className="input-base py-4 pl-12"
                    disabled={disabled}
                    placeholder="Nguyễn"
                  />
                </div>
              </div>
              <div>
                <label htmlFor="lastName" className="mono-label mb-2 block text-muted-foreground">
                  Tên
                </label>
                <input
                  id="lastName"
                  name="lastName"
                  value={formData.lastName}
                  onChange={handleChange}
                  className="input-base py-4"
                  disabled={disabled}
                  placeholder="An"
                />
              </div>
            </div>

            <div>
              <label htmlFor="username" className="mono-label mb-2 block text-muted-foreground">
                Tên đăng nhập
              </label>
              <div className="relative">
                <User className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-outline" />
                <input
                  id="username"
                  name="username"
                  value={formData.username}
                  onChange={handleChange}
                  className="input-base py-4 pl-12"
                  disabled={disabled}
                  placeholder="nguyenvana"
                />
              </div>
            </div>

            <div>
              <label htmlFor="email" className="mono-label mb-2 block text-muted-foreground">
                Email
              </label>
              <div className="relative">
                <Mail className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-outline" />
                <input
                  id="email"
                  name="email"
                  type="email"
                  value={formData.email}
                  onChange={handleChange}
                  className="input-base py-4 pl-12"
                  disabled={disabled}
                  placeholder="ban@example.com"
                />
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="password" className="mono-label mb-2 block text-muted-foreground">
                  Mật khẩu
                </label>
                <div className="relative">
                  <Lock className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-outline" />
                  <input
                    id="password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    value={formData.password}
                    onChange={handleChange}
                    className="input-base py-4 pl-12 pr-11"
                    disabled={disabled}
                    placeholder="Ít nhất 6 ký tự"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((value) => !value)}
                    className="absolute right-4 top-1/2 -translate-y-1/2 text-outline transition hover:text-foreground"
                    disabled={disabled}
                    aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                  >
                    {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
              </div>
              <div>
                <label htmlFor="confirmPassword" className="mono-label mb-2 block text-muted-foreground">
                  Xác nhận
                </label>
                <div className="relative">
                  <Lock className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-outline" />
                  <input
                    id="confirmPassword"
                    name="confirmPassword"
                    type={showConfirmPassword ? "text" : "password"}
                    value={formData.confirmPassword}
                    onChange={handleChange}
                    className="input-base py-4 pl-12 pr-11"
                    disabled={disabled}
                    placeholder="Nhập lại"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword((value) => !value)}
                    className="absolute right-4 top-1/2 -translate-y-1/2 text-outline transition hover:text-foreground"
                    disabled={disabled}
                    aria-label={showConfirmPassword ? "Ẩn mật khẩu xác nhận" : "Hiện mật khẩu xác nhận"}
                  >
                    {showConfirmPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
              </div>
            </div>

            <div className="flex gap-3 rounded-lg border border-outline-variant/30 bg-surface-container-low px-4 py-3">
              <Info className="mt-0.5 h-5 w-5 shrink-0 text-primary" />
              <p className="text-xs leading-6 text-muted-foreground">
                Mật khẩu nên có chữ hoa, chữ thường và số để tăng độ an toàn cho tài khoản.
              </p>
            </div>

            <button type="submit" disabled={disabled} className="btn-primary w-full py-4 group disabled:cursor-not-allowed disabled:opacity-60">
              {disabled ? (
                <>
                  <span className="h-5 w-5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Đang tạo tài khoản...
                </>
              ) : (
                <>
                  Tạo tài khoản
                  <ArrowRight className="h-5 w-5 transition-transform group-hover:translate-x-1" />
                </>
              )}
            </button>
          </form>

          <div className="mt-8 text-center">
            <p className="text-sm text-muted-foreground">
              Đã có tài khoản?{" "}
              <NavLink to="/auth" className="font-bold text-primary transition hover:text-primary-container">
                Đăng nhập
              </NavLink>
            </p>
          </div>
        </div>

        <footer className="mt-auto pt-12 text-center text-xs text-muted-foreground/70">
          Authorized KINETIC account access. Customer data is protected.
        </footer>
      </main>
    </div>
  );
};

export default SignupPage;
