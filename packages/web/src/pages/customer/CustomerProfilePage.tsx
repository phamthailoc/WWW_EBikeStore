import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { authAPI, userAPI } from "@ebike/shared-code/api";
import { useAuth } from "@ebike/shared-code/hooks";
import type { UserAddressRequest, UserAddressResponse, UserProfileResponse } from "@ebike/shared-code/types";
import SectionShell from "../../components/common/SectionShell";

const CustomerProfilePage = () => {
  const navigate = useNavigate();
  const { isAuthenticated, isBootstrapping, logout } = useAuth();
  const [profile, setProfile] = useState<UserProfileResponse | null>(null);
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "" });
  const [passwordForm, setPasswordForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [addressesLoading, setAddressesLoading] = useState(true);
  const [addressSaving, setAddressSaving] = useState(false);
  const [addressDeleting, setAddressDeleting] = useState(false);
  const [error, setError] = useState("");
  const [addressError, setAddressError] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [success, setSuccess] = useState("");
  const [addressSuccess, setAddressSuccess] = useState("");
  const [passwordSuccess, setPasswordSuccess] = useState("");
  const [addresses, setAddresses] = useState<UserAddressResponse[]>([]);
  const [editingAddressId, setEditingAddressId] = useState<number | null>(null);
  const [addressForm, setAddressForm] = useState<UserAddressRequest>({
    addressType: "SHIPPING",
    street: "",
    city: "",
    postalCode: "",
    country: "",
    isDefault: false
  });

  useEffect(() => {
    if (!isBootstrapping && !isAuthenticated) {
      navigate("/auth", { replace: true });
    }
  }, [isAuthenticated, isBootstrapping, navigate]);

  useEffect(() => {
    let mounted = true;
    const loadProfile = async () => {
      if (isBootstrapping || !isAuthenticated) {
        return;
      }

      setLoading(true);
      setError("");
      try {
        const response = await authAPI.getProfile();
        if (!mounted) {
          return;
        }
        setProfile(response);
        setForm({
          firstName: response.firstName ?? "",
          lastName: response.lastName ?? "",
          email: response.email ?? ""
        });
      } catch (loadError) {
        if (mounted) {
          setError(loadError instanceof Error ? loadError.message : "Không thể tải hồ sơ lúc này.");
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    void loadProfile();
    return () => {
      mounted = false;
    };
  }, [isAuthenticated, isBootstrapping]);

  useEffect(() => {
    if (!profile) {
      return;
    }

    let mounted = true;
    const loadAddresses = async () => {
      setAddressesLoading(true);
      setAddressError("");
      try {
        const response = await userAPI.listAddresses(profile.userId);
        if (!mounted) {
          return;
        }
        setAddresses(response);
      } catch (loadError) {
        if (mounted) {
          setAddressError(loadError instanceof Error ? loadError.message : "Không thể tải địa chỉ.");
        }
      } finally {
        if (mounted) {
          setAddressesLoading(false);
        }
      }
    };

    void loadAddresses();
    return () => {
      mounted = false;
    };
  }, [profile]);

  const refreshAddresses = async () => {
    if (!profile) {
      return;
    }
    setAddressesLoading(true);
    setAddressError("");
    try {
      const response = await userAPI.listAddresses(profile.userId);
      setAddresses(response);
    } catch (loadError) {
      setAddressError(loadError instanceof Error ? loadError.message : "Không thể tải địa chỉ.");
    } finally {
      setAddressesLoading(false);
    }
  };

  const resetAddressForm = () => {
    setEditingAddressId(null);
    setAddressForm({
      addressType: "SHIPPING",
      street: "",
      city: "",
      postalCode: "",
      country: "",
      isDefault: false
    });
    setAddressError("");
    setAddressSuccess("");
  };

  const handleEditAddress = (address: UserAddressResponse) => {
    setEditingAddressId(address.id);
    setAddressForm({
      addressType: address.addressType ?? "SHIPPING",
      street: address.street ?? "",
      city: address.city ?? "",
      postalCode: address.postalCode ?? "",
      country: address.country ?? "",
      isDefault: Boolean(address.isDefault)
    });
    setAddressError("");
    setAddressSuccess("");
  };

  const handleAddressSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!profile) {
      setAddressError("Không tìm thấy hồ sơ người dùng.");
      return;
    }

    const street = addressForm.street.trim();
    const city = addressForm.city.trim();
    const postalCode = addressForm.postalCode.trim();
    const country = addressForm.country.trim();

    if (!street || !city || !postalCode || !country) {
      setAddressError("Vui lòng điền đầy đủ thông tin địa chỉ.");
      return;
    }

    setAddressSaving(true);
    setAddressError("");
    setAddressSuccess("");

    try {
      const payload: UserAddressRequest = {
        addressType: addressForm.addressType,
        street,
        city,
        postalCode,
        country,
        isDefault: Boolean(addressForm.isDefault)
      };

      if (editingAddressId) {
        await userAPI.updateAddress(profile.userId, editingAddressId, payload);
        setAddressSuccess("Đã cập nhật địa chỉ.");
      } else {
        await userAPI.createAddress(profile.userId, payload);
        setAddressSuccess("Đã lưu địa chỉ mới.");
      }
      resetAddressForm();
      await refreshAddresses();
    } catch (saveError) {
      setAddressError(saveError instanceof Error ? saveError.message : "Không thể lưu địa chỉ.");
    } finally {
      setAddressSaving(false);
    }
  };

  const handleAddressDelete = async (addressId: number) => {
    if (!profile) {
      setAddressError("Không tìm thấy hồ sơ người dùng.");
      return;
    }
    if (!window.confirm("Bạn có chắc chắn muốn xóa địa chỉ này không?")) {
      return;
    }

    setAddressDeleting(true);
    setAddressError("");
    setAddressSuccess("");
    try {
      await userAPI.deleteAddress(profile.userId, addressId);
      setAddressSuccess("Đã xóa địa chỉ.");
      if (editingAddressId === addressId) {
        resetAddressForm();
      }
      await refreshAddresses();
    } catch (deleteError) {
      setAddressError(deleteError instanceof Error ? deleteError.message : "Không thể xóa địa chỉ.");
    } finally {
      setAddressDeleting(false);
    }
  };

  const displayName = useMemo(() => {
    if (!profile) {
      return "Tài khoản";
    }
    return [profile.firstName, profile.lastName].filter(Boolean).join(" ").trim() || profile.username;
  }, [profile]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const response = await authAPI.updateProfile({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim()
      });
      setProfile(response);
      setSuccess("Đã cập nhật hồ sơ.");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Không thể cập nhật hồ sơ.");
    } finally {
      setSaving(false);
    }
  };

  const handlePasswordSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPasswordSaving(true);
    setPasswordError("");
    setPasswordSuccess("");

    const currentPassword = passwordForm.currentPassword.trim();
    const newPassword = passwordForm.newPassword.trim();
    const confirmPassword = passwordForm.confirmPassword.trim();

    if (!currentPassword || !newPassword || !confirmPassword) {
      setPasswordError("Vui lòng nhập đầy đủ thông tin mật khẩu.");
      setPasswordSaving(false);
      return;
    }

    try {
      await authAPI.changePassword({
        currentPassword,
        newPassword,
        confirmPassword
      });
      setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
      setPasswordSuccess("Đã đổi mật khẩu.");
    } catch (changeError) {
      setPasswordError(changeError instanceof Error ? changeError.message : "Không thể đổi mật khẩu.");
    } finally {
      setPasswordSaving(false);
    }
  };

  return (
    <SectionShell
      eyebrow="Hồ sơ khách hàng"
      title="Thông tin tài khoản"
      description="Quản lý thông tin cá nhân, trạng thái tài khoản và các quyền hiện đang được gán."
    >
      <div className="grid gap-6 px-0 py-6 lg:grid-cols-12">
        <section className="rounded-lg border border-outline-variant/15 bg-white p-6 shadow-sm lg:col-span-8">
          {loading ? (
            <p className="text-muted-foreground">Đang tải hồ sơ...</p>
          ) : (
            <>
              <div className="mb-6">
                <p className="text-sm uppercase tracking-wider text-muted-foreground">Xin chào</p>
                <h2 className="mt-1 text-3xl font-bold">{displayName}</h2>
                <p className="mt-2 text-sm text-muted-foreground">{profile?.email}</p>
              </div>

              {error ? <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div> : null}
              {success ? <div className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">{success}</div> : null}

              <form className="space-y-5" onSubmit={handleSubmit}>
                <div className="grid gap-5 sm:grid-cols-2">
                  <label className="space-y-2">
                    <span className="text-sm font-semibold text-muted-foreground">Tên</span>
                    <input
                      value={form.firstName}
                      onChange={(event) => setForm((current) => ({ ...current, firstName: event.target.value }))}
                      className="input-base"
                    />
                  </label>
                  <label className="space-y-2">
                    <span className="text-sm font-semibold text-muted-foreground">Họ</span>
                    <input
                      value={form.lastName}
                      onChange={(event) => setForm((current) => ({ ...current, lastName: event.target.value }))}
                      className="input-base"
                    />
                  </label>
                </div>
                <label className="space-y-2 block">
                  <span className="text-sm font-semibold text-muted-foreground">Email</span>
                  <input
                    type="email"
                    value={form.email}
                    onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
                    className="input-base"
                  />
                </label>
                <div className="flex flex-wrap gap-3">
                  <button type="submit" disabled={saving} className="btn-primary disabled:cursor-not-allowed disabled:opacity-60">
                    {saving ? "Đang lưu..." : "Lưu thay đổi"}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      logout();
                      setProfile(null);
                    }}
                    className="btn-secondary"
                  >
                    Đăng xuất
                  </button>
                </div>
              </form>
            </>
          )}
        </section>

        <section className="rounded-lg border border-outline-variant/15 bg-white p-6 shadow-sm lg:col-span-8">
          <div className="mb-6">
            <h3 className="text-xl font-bold">Đổi mật khẩu</h3>
            <p className="mt-2 text-sm text-muted-foreground">Cập nhật mật khẩu đăng nhập để bảo vệ tài khoản của bạn.</p>
          </div>

          {passwordError ? <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{passwordError}</div> : null}
          {passwordSuccess ? <div className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">{passwordSuccess}</div> : null}

          <form className="space-y-5" onSubmit={handlePasswordSubmit}>
            <label className="space-y-2 block">
              <span className="text-sm font-semibold text-muted-foreground">Mật khẩu hiện tại</span>
              <input
                type="password"
                value={passwordForm.currentPassword}
                onChange={(event) => setPasswordForm((current) => ({ ...current, currentPassword: event.target.value }))}
                className="input-base"
              />
            </label>

            <div className="grid gap-5 sm:grid-cols-2">
              <label className="space-y-2">
                <span className="text-sm font-semibold text-muted-foreground">Mật khẩu mới</span>
                <input
                  type="password"
                  value={passwordForm.newPassword}
                  onChange={(event) => setPasswordForm((current) => ({ ...current, newPassword: event.target.value }))}
                  className="input-base"
                />
              </label>
              <label className="space-y-2">
                <span className="text-sm font-semibold text-muted-foreground">Xác nhận mật khẩu mới</span>
                <input
                  type="password"
                  value={passwordForm.confirmPassword}
                  onChange={(event) => setPasswordForm((current) => ({ ...current, confirmPassword: event.target.value }))}
                  className="input-base"
                />
              </label>
            </div>

            <button type="submit" disabled={passwordSaving} className="btn-secondary disabled:cursor-not-allowed disabled:opacity-60">
              {passwordSaving ? "Đang đổi mật khẩu..." : "Đổi mật khẩu"}
            </button>
          </form>
        </section>

        <section className="rounded-lg border border-outline-variant/15 bg-white p-6 shadow-sm lg:col-span-8">
          <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h3 className="text-xl font-bold">Địa chỉ giao hàng</h3>
              <p className="mt-2 text-sm text-muted-foreground">Quản lý địa chỉ đã lưu và đánh dấu địa chỉ mặc định để sử dụng khi thanh toán.</p>
            </div>
            {editingAddressId ? (
              <button type="button" onClick={resetAddressForm} className="btn-secondary">
                Hủy chỉnh sửa
              </button>
            ) : null}
          </div>

          {addressError ? <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{addressError}</div> : null}
          {addressSuccess ? <div className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">{addressSuccess}</div> : null}

          <form className="space-y-5" onSubmit={handleAddressSubmit}>
            <div className="grid gap-5 sm:grid-cols-2">
              <label className="space-y-2">
                <span className="text-sm font-semibold text-muted-foreground">Loại địa chỉ</span>
                <select
                  value={addressForm.addressType ?? "SHIPPING"}
                  onChange={(event) => setAddressForm((current) => ({ ...current, addressType: event.target.value }))}
                  className="input-base"
                >
                  <option value="SHIPPING">Giao hàng</option>
                  <option value="BILLING">Hóa đơn</option>
                </select>
              </label>
              <label className="space-y-2">
                <span className="text-sm font-semibold text-muted-foreground">Thành phố</span>
                <input
                  value={addressForm.city}
                  onChange={(event) => setAddressForm((current) => ({ ...current, city: event.target.value }))}
                  className="input-base"
                />
              </label>
            </div>

            <div className="grid gap-5 sm:grid-cols-2">
              <label className="space-y-2">
                <span className="text-sm font-semibold text-muted-foreground">Số nhà, tên đường</span>
                <input
                  value={addressForm.street}
                  onChange={(event) => setAddressForm((current) => ({ ...current, street: event.target.value }))}
                  className="input-base"
                />
              </label>
              <label className="space-y-2">
                <span className="text-sm font-semibold text-muted-foreground">Mã bưu điện</span>
                <input
                  value={addressForm.postalCode}
                  onChange={(event) => setAddressForm((current) => ({ ...current, postalCode: event.target.value }))}
                  className="input-base"
                />
              </label>
            </div>

            <label className="space-y-2 block">
              <span className="text-sm font-semibold text-muted-foreground">Quốc gia</span>
              <input
                value={addressForm.country}
                onChange={(event) => setAddressForm((current) => ({ ...current, country: event.target.value }))}
                className="input-base"
              />
            </label>

            <label className="flex items-center gap-3 text-sm font-medium text-muted-foreground">
              <input
                type="checkbox"
                checked={Boolean(addressForm.isDefault)}
                onChange={(event) => setAddressForm((current) => ({ ...current, isDefault: event.target.checked }))}
                className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
              />
              Đặt làm địa chỉ mặc định
            </label>

            <div className="flex flex-wrap gap-3">
              <button type="submit" disabled={addressSaving} className="btn-primary disabled:cursor-not-allowed disabled:opacity-60">
                {addressSaving ? "Đang lưu..." : editingAddressId ? "Cập nhật địa chỉ" : "Lưu địa chỉ mới"}
              </button>
              {editingAddressId ? (
                <button type="button" onClick={resetAddressForm} className="btn-secondary">
                  Hủy
                </button>
              ) : null}
            </div>
          </form>

          <div className="mt-8">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <h4 className="text-lg font-bold">Địa chỉ đã lưu</h4>
                <p className="text-sm text-muted-foreground">Bạn có thể chỉnh sửa hoặc xoá địa chỉ lưu sẵn.</p>
              </div>
              <span className="text-sm text-muted-foreground">{addresses.length} địa chỉ</span>
            </div>

            {addressesLoading ? (
              <p className="text-sm text-muted-foreground">Đang tải địa chỉ...</p>
            ) : addresses.length === 0 ? (
              <p className="text-sm text-muted-foreground">Chưa có địa chỉ nào. Hãy thêm địa chỉ mới để sử dụng khi thanh toán.</p>
            ) : (
              <div className="space-y-4">
                {addresses.map((address) => (
                  <div key={address.id} className="rounded-xl border border-outline-variant/15 bg-surface-container-low p-4">
                    <div className="mb-2 flex items-start justify-between gap-4">
                      <div>
                        <p className="font-semibold">{address.addressType === "BILLING" ? "Hóa đơn" : "Giao hàng"}</p>
                        <p className="text-sm text-muted-foreground">{address.street}, {address.city}, {address.postalCode}, {address.country}</p>
                      </div>
                      {address.isDefault ? (
                        <span className="rounded-full bg-primary/10 px-2 py-1 text-[11px] font-semibold text-primary">Mặc định</span>
                      ) : null}
                    </div>
                    <div className="flex flex-wrap gap-3">
                      <button
                        type="button"
                        onClick={() => handleEditAddress(address)}
                        className="rounded-full border border-outline-variant/20 bg-white px-3 py-2 text-sm font-semibold text-foreground transition hover:border-primary"
                      >
                        Chỉnh sửa
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleAddressDelete(address.id)}
                        disabled={addressDeleting}
                        className="rounded-full border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 transition hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        Xóa
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>

        <aside className="space-y-6 lg:col-span-4 lg:col-start-9 lg:row-start-1">
          <div className="rounded-lg border border-outline-variant/15 bg-white p-6 shadow-sm">
            <h3 className="mb-4 text-xl font-bold">Trạng thái</h3>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-md bg-surface-container-low p-4">
                <p className="text-sm text-muted-foreground">Tài khoản</p>
                <p className="mt-1 font-bold">{profile?.active ? "Đang hoạt động" : "Tạm khóa"}</p>
              </div>
              <div className="rounded-md bg-surface-container-low p-4">
                <p className="text-sm text-muted-foreground">Xác minh</p>
                <p className="mt-1 font-bold">{profile?.verified ? "Đã xác minh" : "Chưa xác minh"}</p>
              </div>
            </div>
          </div>

          <div className="rounded-lg border border-outline-variant/15 bg-white p-6 shadow-sm">
            <h3 className="mb-4 text-xl font-bold">Vai trò</h3>
            <div className="flex flex-wrap gap-2">
              {(profile?.roles ?? []).map((role) => (
                <span key={role} className="rounded-full bg-primary/10 px-3 py-1 text-xs font-bold text-primary">
                  {role}
                </span>
              ))}
            </div>
          </div>
        </aside>
      </div>
    </SectionShell>
  );
};

export default CustomerProfilePage;
