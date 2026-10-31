import { Clock, CreditCard, Lock, MailCheck, MapPin, Phone, Store, Zap } from "lucide-react";
import { orderAPI, paymentAPI, showroomAPI, userAPI } from "@ebike/shared-code/api";
import { useAppSelector } from "@ebike/shared-code/redux";
import type { OrderQuote, Showroom, UserAddressResponse } from "@ebike/shared-code/types";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { attachImageFallback, resolveProductImage } from "../utils/media";

interface CheckoutState {
  product?: {
    id: number;
    name: string;
    slug?: string;
    price: number;
    image?: string;
    categoryName?: string;
  };
  selectedColor?: string;
  quantity?: number;
}

const mapCheckoutErrorToFields = (message: string) => {
  const lowerMessage = message.toLowerCase();
  const errors: Record<string, string> = {};

  if (lowerMessage.includes("họ và tên")) {
    errors.customerName = message;
  }
  if (lowerMessage.includes("số điện thoại")) {
    errors.phoneNumber = message;
  }
  if (lowerMessage.includes("email")) {
    errors.customerEmail = message;
  }
  if (lowerMessage.includes("địa chỉ")) {
    errors.detailedAddress = message;
  }
  if (lowerMessage.includes("showroom")) {
    errors.pickupShowroomId = message;
  }
  if (lowerMessage.includes("ghi chú")) {
    errors.notes = message;
  }

  return errors;
};

const DISTRICT_DISPLAY_NAMES: Record<string, string> = {
  "Go Vap": "Gò Vấp",
  "Quan 1": "Quận 1",
  "Quan 3": "Quận 3",
  "Quan 7": "Quận 7",
  "Tan Binh": "Tân Bình",
  "Thu Duc": "Thủ Đức"
};

const formatDistrictName = (district: string) => DISTRICT_DISPLAY_NAMES[district] ?? district;

const CheckoutPage = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const authUser = useAppSelector((state) => state.auth.user);
  const state = (location.state as CheckoutState | undefined) ?? {};
  const product = state.product;
  const quantity = state.quantity ?? 1;
  const [showrooms, setShowrooms] = useState<Showroom[]>([]);
  const [orderQuote, setOrderQuote] = useState<OrderQuote | null>(null);
  const [loadingQuote, setLoadingQuote] = useState(true);
  const [loadingShowrooms, setLoadingShowrooms] = useState(true);
  const [loadingAddresses, setLoadingAddresses] = useState(true);
  const [savedAddresses, setSavedAddresses] = useState<UserAddressResponse[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [selectedPaymentMethod, setSelectedPaymentMethod] = useState<"PAY_LATER" | "VNPAY">("PAY_LATER");
  const [registrationService, setRegistrationService] = useState<"SELF" | "SHOWROOM">("SELF");
  const [emailOtpSessionId, setEmailOtpSessionId] = useState<string | null>(null);
  const [emailOtpCode, setEmailOtpCode] = useState("");
  const [emailOtpVerifiedFor, setEmailOtpVerifiedFor] = useState<string | null>(null);
  const [emailOtpMessage, setEmailOtpMessage] = useState<string | null>(null);
  const [emailOtpLoading, setEmailOtpLoading] = useState(false);
  const [discountCode, setDiscountCode] = useState("");
  const [appliedDiscountCode, setAppliedDiscountCode] = useState<string | null>(null);
  const [discountCodeError, setDiscountCodeError] = useState<string | null>(null);
  const [discountCodeMessage, setDiscountCodeMessage] = useState<string | null>(null);
  const [form, setForm] = useState({
    customerName: authUser?.fullName ?? "",
    phoneNumber: "",
    customerEmail: authUser?.email ?? "",
    district: "",
    pickupShowroomId: "",
    detailedAddress: "",
    notes: ""
  });

  useEffect(() => {
    if (!product) {
      navigate("/products", { replace: true });
    }
  }, [navigate, product]);

  useEffect(() => {
    setForm((current) => ({
      ...current,
      customerName: current.customerName || authUser?.fullName || "",
      customerEmail: current.customerEmail || authUser?.email || ""
    }));
  }, [authUser?.email, authUser?.fullName]);

  useEffect(() => {
    const loadShowrooms = async () => {
      try {
        const data = await showroomAPI.list();
        setShowrooms(data);
      } catch (error) {
        setSubmitError(error instanceof Error ? error.message : "Không thể tải danh sách showroom.");
      } finally {
        setLoadingShowrooms(false);
      }
    };

    void loadShowrooms();
  }, []);

  useEffect(() => {
    if (!authUser?.id) {
      setLoadingAddresses(false);
      return;
    }

    let mounted = true;
    const loadAddresses = async () => {
      try {
        const response = await userAPI.listAddresses(Number(authUser.id));
        if (!mounted) {
          return;
        }
        setSavedAddresses(response);
      } catch (error) {
        if (mounted) {
          setSubmitError(error instanceof Error ? error.message : "Không thể tải địa chỉ đã lưu.");
        }
      } finally {
        if (mounted) {
          setLoadingAddresses(false);
        }
      }
    };

    void loadAddresses();
    return () => {
      mounted = false;
    };
  }, [authUser?.id]);

  useEffect(() => {
    const loadQuote = async () => {
      if (!product) {
        return;
      }

      setLoadingQuote(true);
      setSubmitError(null);
      try {
        const quote = await orderAPI.quote({
          includeRegistrationService: registrationService === "SHOWROOM",
          discountCode: appliedDiscountCode ?? undefined,
          items: [
            {
              productId: product.id,
              quantity
            }
          ]
        });
        setOrderQuote(quote);
        setDiscountCodeError(null);
        setDiscountCodeMessage(appliedDiscountCode ? `Mã giảm giá ${appliedDiscountCode} đã được áp dụng.` : null);
      } catch (error) {
        const message = error instanceof Error ? error.message : "Không thể tính tổng thanh toán.";
        if (appliedDiscountCode) {
          setDiscountCodeError(message);
          setDiscountCodeMessage(null);
        } else {
          setOrderQuote(null);
          setSubmitError(message);
        }
      } finally {
        setLoadingQuote(false);
      }
    };

    void loadQuote();
  }, [product, quantity, registrationService, appliedDiscountCode]);

  const fallbackSubtotal = product ? product.price * quantity : 0;
  const subtotal = orderQuote?.subtotal ?? fallbackSubtotal;
  const totalDiscount = orderQuote?.discountAmount ?? 0;
  const registrationFee = orderQuote?.registrationFee ?? 0;
  const total = orderQuote?.totalAmount ?? fallbackSubtotal;

  const districts = useMemo(
    () =>
      Array.from(new Set(showrooms.map((showroom) => showroom.district))).sort((left, right) =>
        formatDistrictName(left).localeCompare(formatDistrictName(right), "vi")
      ),
    [showrooms]
  );

  const filteredShowrooms = useMemo(
    () => showrooms.filter((showroom) => showroom.district === form.district),
    [form.district, showrooms]
  );

  const selectedShowroom = filteredShowrooms.find((showroom) => String(showroom.id) === form.pickupShowroomId) ?? null;

  const applySavedAddress = (address: UserAddressResponse | null) => {
    setSelectedAddressId(address?.id ?? null);
    if (!address) {
      return;
    }
    const newDetailedAddress = [address.street, address.city, address.postalCode, address.country]
      .filter(Boolean)
      .join(", ");
    setForm((current) => ({ ...current, detailedAddress: newDetailedAddress }));
  };

  const inputClassName = (hasError: boolean) =>
    `w-full border-0 border-b-2 px-0 py-2 font-medium focus:ring-0 ${
      hasError
        ? "border-red-500 bg-red-50/40 text-red-700 focus:border-red-500"
        : "border-outline-variant bg-transparent focus:border-primary"
    }`;

  const selectClassName = (hasError: boolean) =>
    `w-full rounded-lg border px-4 py-3 text-sm font-medium focus:outline-none ${
      hasError
        ? "border-red-500 bg-red-50/40 text-red-700 focus:border-red-500"
        : "border-outline-variant/20 bg-white focus:border-primary"
    }`;

  const updateForm = (field: keyof typeof form, value: string) => {
    setFieldErrors((current) => {
      if (!current[field]) {
        return current;
      }

      const next = { ...current };
      delete next[field];
      return next;
    });
    if (field === "customerEmail") {
      setEmailOtpSessionId(null);
      setEmailOtpCode("");
      setEmailOtpVerifiedFor(null);
      setEmailOtpMessage(null);
    }
    setForm((current) => ({ ...current, [field]: value }));
  };

  const sendEmailOtp = async () => {
    setSubmitError(null);
    setEmailOtpMessage(null);
    const email = form.customerEmail.trim();
    if (!email) {
      setFieldErrors((current) => ({ ...current, customerEmail: "Vui lòng nhập email trước khi gửi OTP." }));
      return null;
    }

    setEmailOtpLoading(true);
    try {
      const response = await orderAPI.sendEmailOtp(email);
      setEmailOtpSessionId(response.verificationSessionId);
      setEmailOtpCode("");
      setEmailOtpVerifiedFor(null);
      setEmailOtpMessage(`Đã gửi mã OTP tới ${response.email}. Nếu đang chạy dev, mã cũng được in trong log backend.`);
      return response.verificationSessionId;
    } catch (error) {
      const message = error instanceof Error ? error.message : "Không thể gửi mã OTP.";
      setSubmitError(message);
      return null;
    } finally {
      setEmailOtpLoading(false);
    }
  };

  const verifyEmailOtp = async (sessionId: string) => {
    const code = emailOtpCode.trim();
    if (!code) {
      setFieldErrors((current) => ({ ...current, emailOtpCode: "Vui lòng nhập mã OTP đã gửi qua email." }));
      return false;
    }

    setEmailOtpLoading(true);
    try {
      const response = await orderAPI.verifyEmailOtp(sessionId, code);
      setEmailOtpVerifiedFor(response.email);
      setEmailOtpMessage("Email đã được xác thực. Đang tạo đơn hàng...");
      return true;
    } catch (error) {
      const message = error instanceof Error ? error.message : "Mã OTP chưa hợp lệ.";
      setFieldErrors((current) => ({ ...current, emailOtpCode: message }));
      setSubmitError(message);
      return false;
    } finally {
      setEmailOtpLoading(false);
    }
  };

  const handleDistrictChange = (district: string) => {
    setFieldErrors((current) => {
      const next = { ...current };
      delete next.district;
      delete next.pickupShowroomId;
      return next;
    });
    setForm((current) => ({
      ...current,
      district,
      pickupShowroomId: ""
    }));
  };

  const handleSubmit = async () => {
    setSubmitError(null);
    setFieldErrors({});

    if (!product) {
      setSubmitError("Không có sản phẩm để đặt.");
      return;
    }

    const nextFieldErrors: Record<string, string> = {};
    if (!form.customerName.trim()) nextFieldErrors.customerName = "Vui lòng nhập họ và tên.";
    if (!form.phoneNumber.trim()) nextFieldErrors.phoneNumber = "Vui lòng nhập số điện thoại.";
    if (!form.customerEmail.trim()) nextFieldErrors.customerEmail = "Vui lòng nhập email.";
    if (!form.district) nextFieldErrors.district = "Vui lòng chọn quận / huyện.";
    if (!form.pickupShowroomId) nextFieldErrors.pickupShowroomId = "Vui lòng chọn showroom nhận xe.";
    if (!form.detailedAddress.trim()) nextFieldErrors.detailedAddress = "Vui lòng nhập địa chỉ cụ thể.";

    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors);
      setSubmitError("Vui lòng kiểm tra các trường đang được tô đỏ.");
      return;
    }

    const normalizedDiscountCode = discountCode.trim().toUpperCase();
    if (normalizedDiscountCode && normalizedDiscountCode !== appliedDiscountCode) {
      setSubmitError("Vui lòng bấm 'Áp dụng' để kiểm tra mã giảm giá trước khi đặt hàng.");
      return;
    }
    if (discountCodeError) {
      setSubmitError("Vui lòng kiểm tra mã giảm giá trước khi đặt hàng.");
      return;
    }

    let verifiedSessionId = emailOtpSessionId;
    const normalizedEmail = form.customerEmail.trim().toLowerCase();
    if (emailOtpVerifiedFor !== normalizedEmail) {
      if (!verifiedSessionId) {
        await sendEmailOtp();
        setSubmitError("Vui lòng nhập mã OTP đã gửi tới email rồi bấm xác nhận đặt hàng lần nữa.");
        return;
      }
      const verified = await verifyEmailOtp(verifiedSessionId);
      if (!verified) {
        return;
      }
    }

    if (!verifiedSessionId) {
      setSubmitError("Vui lòng xác thực email trước khi đặt hàng.");
      return;
    }

    setSubmitting(true);
    try {
      const notes = form.notes.trim();
      const createdOrder = await orderAPI.create({
        userId: authUser?.id ? Number(authUser.id) : undefined,
        customerName: form.customerName.trim(),
        phoneNumber: form.phoneNumber.trim(),
        customerEmail: form.customerEmail.trim(),
        emailVerificationSessionId: verifiedSessionId,
        pickupShowroomId: Number(form.pickupShowroomId),
        detailedAddress: form.detailedAddress.trim(),
        paymentMethod: selectedPaymentMethod,
        discountCode: appliedDiscountCode ?? undefined,
        includeRegistrationService: registrationService === "SHOWROOM",
        notes,
        items: [
          {
            productId: product.id,
            quantity
          }
        ]
      });

      setOrderQuote({
        subtotal: createdOrder.subtotal,
        shippingFee: createdOrder.shippingFee,
        discountAmount: createdOrder.discountAmount,
        registrationFee: createdOrder.registrationFee,
        totalAmount: createdOrder.totalAmount
      });

      if (selectedPaymentMethod === "VNPAY") {
        const payment = await paymentAPI.createVnPayPayment({
          orderId: createdOrder.id,
          language: "vn"
        });
        window.location.assign(payment.paymentUrl);
        return;
      }

      navigate("/checkout/success", {
        replace: true,
        state: {
          order: createdOrder
        }
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Không thể tạo đơn hàng.";
      const backendFieldErrors = mapCheckoutErrorToFields(message);
      if (Object.keys(backendFieldErrors).length > 0) {
        setFieldErrors((current) => ({ ...current, ...backendFieldErrors }));
      }
      setSubmitError(`Thông tin chưa hợp lệ: ${message}`);
    } finally {
      setSubmitting(false);
    }
  };

  if (!product) {
    return null;
  }

  return (
    <div className="mx-auto min-h-screen max-w-screen-2xl px-6 pb-24 pt-32 md:px-12">
      <div className="mb-16">
        <span className="mono-label mb-4 block text-primary">Secure Checkout</span>
        <h1 className="text-5xl font-bold tracking-tighter md:text-6xl">
          Nhận xe tại showroom <br />
          phù hợp cho bạn ở TP.HCM.
        </h1>
      </div>

      <div className="grid grid-cols-1 gap-16 lg:grid-cols-12">
        <div className="space-y-12 lg:col-span-7">
          {submitError ? (
            <div className="rounded-xl border border-red-200 bg-red-50 px-5 py-4 text-sm text-red-700">{submitError}</div>
          ) : null}

          <section>
            <h2 className="mb-8 text-2xl font-bold tracking-tight">Phương thức thanh toán</h2>
            <div className="space-y-4">
              <div className={`rounded-xl border-2 p-6 ${selectedPaymentMethod === "PAY_LATER" ? "border-primary bg-surface-container-low" : "border-transparent bg-surface-container-low"}`}>
                <label className="flex cursor-pointer items-start gap-4">
                  <input
                    type="radio"
                    name="payment"
                    checked={selectedPaymentMethod === "PAY_LATER"}
                    onChange={() => setSelectedPaymentMethod("PAY_LATER")}
                    className="mt-1 text-primary focus:ring-primary"
                  />
                  <div className="flex-grow">
                    <div className="mb-1 flex items-center justify-between">
                      <span className="text-lg font-bold">Thanh toán sau</span>
                      <Clock className="text-primary" />
                    </div>
                    <p className="text-sm text-muted-foreground">
                      Showroom sẽ xác nhận đơn và hướng dẫn thanh toán sau. Đơn hàng được ghi nhận là chưa thanh toán.
                    </p>
                  </div>
                </label>
              </div>

              <div className={`rounded-xl p-6 transition-all hover:bg-surface-container-high ${selectedPaymentMethod === "VNPAY" ? "border-2 border-primary bg-surface-container-low" : "bg-surface-container-low"}`}>
                <label className="flex cursor-pointer items-start gap-4">
                  <input
                    type="radio"
                    name="payment"
                    checked={selectedPaymentMethod === "VNPAY"}
                    onChange={() => setSelectedPaymentMethod("VNPAY")}
                    className="mt-1 text-primary focus:ring-primary"
                  />
                  <div className="flex-grow">
                    <div className="mb-1 flex items-center justify-between">
                      <span className="text-lg font-bold">VNPay</span>
                      <CreditCard className="text-primary" />
                    </div>
                    <p className="text-sm text-muted-foreground">Thanh toán qua cổng VNPay bằng ATM, QR hoặc thẻ nội địa.</p>
                  </div>
                </label>
              </div>
            </div>
          </section>

          <section className="rounded-xl border border-outline-variant/15 bg-white p-8">
            <h3 className="mb-6 text-xl font-bold">Thông tin khách hàng</h3>
            <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
              <div className="space-y-2">
                <label className="mono-label text-muted-foreground">Họ và tên</label>
                <input
                  type="text"
                  value={form.customerName}
                  onChange={(event) => updateForm("customerName", event.target.value)}
                  className={inputClassName(Boolean(fieldErrors.customerName))}
                />
                {fieldErrors.customerName ? <p className="text-sm text-red-600">{fieldErrors.customerName}</p> : null}
              </div>
              <div className="space-y-2">
                <label className="mono-label text-muted-foreground">Số điện thoại</label>
                <input
                  type="tel"
                  value={form.phoneNumber}
                  onChange={(event) => updateForm("phoneNumber", event.target.value)}
                  className={inputClassName(Boolean(fieldErrors.phoneNumber))}
                />
                {fieldErrors.phoneNumber ? <p className="text-sm text-red-600">{fieldErrors.phoneNumber}</p> : null}
              </div>
              <div className="space-y-2">
                <label className="mono-label text-muted-foreground">Email</label>
                <input
                  type="email"
                  value={form.customerEmail}
                  onChange={(event) => updateForm("customerEmail", event.target.value)}
                  className={inputClassName(Boolean(fieldErrors.customerEmail))}
                />
                {fieldErrors.customerEmail ? <p className="text-sm text-red-600">{fieldErrors.customerEmail}</p> : null}
              </div>
              <div className="space-y-3 md:col-span-2">
                <div className="flex flex-col gap-3 rounded-lg border border-outline-variant/15 bg-surface-container-low p-4 md:flex-row md:items-end">
                  <div className="flex-grow space-y-2">
                    <label className="mono-label text-muted-foreground">Mã OTP email</label>
                    <input
                      type="text"
                      inputMode="numeric"
                      maxLength={6}
                      value={emailOtpCode}
                      onChange={(event) => {
                        setFieldErrors((current) => {
                          const next = { ...current };
                          delete next.emailOtpCode;
                          return next;
                        });
                        setEmailOtpCode(event.target.value.replace(/\D/g, "").slice(0, 6));
                      }}
                      placeholder="Nhập 6 chữ số"
                      className={inputClassName(Boolean(fieldErrors.emailOtpCode))}
                    />
                    {fieldErrors.emailOtpCode ? <p className="text-sm text-red-600">{fieldErrors.emailOtpCode}</p> : null}
                    {emailOtpMessage ? <p className="text-sm text-muted-foreground">{emailOtpMessage}</p> : null}
                  </div>
                  <button
                    type="button"
                    onClick={() => void sendEmailOtp()}
                    disabled={emailOtpLoading || submitting}
                    className="inline-flex items-center justify-center gap-2 rounded-lg border border-outline-variant/20 bg-white px-4 py-3 text-sm font-bold transition hover:border-primary disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <MailCheck size={16} />
                    {emailOtpSessionId ? "Gửi lại mã" : "Gửi OTP"}
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section className="rounded-xl border border-outline-variant/15 bg-white p-8">
            <div className="mb-6 flex items-center gap-3">
              <MapPin className="text-primary" />
              <div>
                <h3 className="text-xl font-bold">Địa chỉ đã lưu</h3>
                <p className="mt-2 text-sm text-muted-foreground">Chọn địa chỉ đã lưu để tự động điền vào hộp địa chỉ cụ thể.</p>
              </div>
            </div>

            {loadingAddresses ? (
              <p className="text-sm text-muted-foreground">Đang tải địa chỉ đã lưu...</p>
            ) : savedAddresses.length === 0 ? (
              <p className="text-sm text-muted-foreground">Bạn chưa có địa chỉ lưu. Vui lòng thêm trong hồ sơ khách hàng.</p>
            ) : (
              <div className="space-y-4">
                {savedAddresses.map((address) => (
                  <div
                    key={address.id}
                    className={`rounded-xl border p-4 ${selectedAddressId === address.id ? "border-primary bg-primary/5" : "border-outline-variant/20 bg-white"}`}
                  >
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <p className="font-semibold">
                          {address.addressType === "BILLING" ? "Hóa đơn" : "Giao hàng"}
                          {address.isDefault ? " • Mặc định" : ""}
                        </p>
                        <p className="text-sm text-muted-foreground">
                          {address.street}, {address.city}, {address.postalCode}, {address.country}
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => applySavedAddress(address)}
                        className="inline-flex items-center justify-center rounded-full border border-outline-variant/20 bg-white px-4 py-2 text-sm font-semibold text-foreground transition hover:border-primary"
                      >
                        {selectedAddressId === address.id ? "Đã chọn" : "Dùng địa chỉ này"}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="rounded-xl border border-outline-variant/15 bg-white p-8">
            <div className="mb-6 flex items-center gap-3">
              <Store className="text-primary" />
              <h3 className="text-xl font-bold">Showroom nhận xe</h3>
            </div>

            <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
              <div className="space-y-2">
                <label className="mono-label text-muted-foreground">Thành phố</label>
                <input
                  type="text"
                  value="TP. Hồ Chí Minh"
                  disabled
                  className="w-full rounded-lg border border-outline-variant/20 bg-surface-container-low px-4 py-3 text-sm font-medium text-foreground"
                />
              </div>
              <div className="space-y-2">
                <label className="mono-label text-muted-foreground">Chọn quận / huyện</label>
                <select
                  value={form.district}
                  onChange={(event) => handleDistrictChange(event.target.value)}
                  className={selectClassName(Boolean(fieldErrors.district))}
                >
                  <option value="">{loadingShowrooms ? "Đang tải quận..." : "Chọn quận / huyện"}</option>
                  {districts.map((district) => (
                    <option key={district} value={district}>
                      {formatDistrictName(district)}
                    </option>
                  ))}
                </select>
                {fieldErrors.district ? <p className="text-sm text-red-600">{fieldErrors.district}</p> : null}
              </div>
              <div className="space-y-2 md:col-span-2">
                <label className="mono-label text-muted-foreground">Chọn showroom</label>
                <select
                  value={form.pickupShowroomId}
                  onChange={(event) => updateForm("pickupShowroomId", event.target.value)}
                  disabled={!form.district}
                  className={`${selectClassName(Boolean(fieldErrors.pickupShowroomId))} disabled:cursor-not-allowed disabled:bg-surface-container-low`}
                >
                  <option value="">{form.district ? "Chọn showroom nhận xe" : "Vui lòng chọn quận trước"}</option>
                  {filteredShowrooms.map((showroom) => (
                    <option key={showroom.id} value={showroom.id}>
                      {showroom.name}
                    </option>
                  ))}
                </select>
                {fieldErrors.pickupShowroomId ? <p className="text-sm text-red-600">{fieldErrors.pickupShowroomId}</p> : null}
              </div>
              <div className="space-y-2 md:col-span-2">
                <label className="mono-label text-muted-foreground">Địa chỉ cụ thể</label>
                <textarea
                  rows={4}
                  value={form.detailedAddress}
                  onChange={(event) => updateForm("detailedAddress", event.target.value)}
                  placeholder="Nhập số nhà, tên đường, phường/xã tại TP.HCM để showroom liên hệ nhận xe."
                  className={selectClassName(Boolean(fieldErrors.detailedAddress))}
                />
                {fieldErrors.detailedAddress ? <p className="text-sm text-red-600">{fieldErrors.detailedAddress}</p> : null}
              </div>
            </div>

            {selectedShowroom ? (
              <div className="mt-6 rounded-xl bg-surface-container-low p-5 text-sm text-muted-foreground">
                <div className="mb-2 flex items-center gap-2 font-bold text-foreground">
                  <MapPin size={16} className="text-primary" />
                  {selectedShowroom.name}
                </div>
                <p>{selectedShowroom.address}</p>
                <p className="mt-2 flex items-center gap-2">
                  <Phone size={14} />
                  {selectedShowroom.phone || "Đang cập nhật"}
                </p>
              </div>
            ) : null}
          </section>

          <section className="rounded-xl border border-outline-variant/15 bg-white p-8">
            <h3 className="mb-6 text-xl font-bold">Dịch vụ đăng ký xe</h3>
            <div className="space-y-4">
              <label
                className={`flex cursor-pointer items-start gap-4 rounded-xl border-2 p-5 transition-all ${
                  registrationService === "SELF" ? "border-primary bg-surface-container-low" : "border-outline-variant/15 bg-white"
                }`}
              >
                <input
                  type="radio"
                  name="registrationService"
                  checked={registrationService === "SELF"}
                  onChange={() => setRegistrationService("SELF")}
                  className="mt-1 text-primary focus:ring-primary"
                />
                <div className="flex-grow">
                  <div className="mb-1 flex items-center justify-between gap-4">
                    <span className="text-base font-bold">Tôi tự làm giấy tờ xe</span>
                    <span className="font-mono text-sm font-bold text-primary">0đ</span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    Không cộng phí đăng ký vào đơn hàng. Khách hàng tự thực hiện thủ tục đăng ký xe sau khi nhận xe.
                  </p>
                </div>
              </label>

              <label
                className={`flex cursor-pointer items-start gap-4 rounded-xl border-2 p-5 transition-all ${
                  registrationService === "SHOWROOM" ? "border-primary bg-surface-container-low" : "border-outline-variant/15 bg-white"
                }`}
              >
                <input
                  type="radio"
                  name="registrationService"
                  checked={registrationService === "SHOWROOM"}
                  onChange={() => setRegistrationService("SHOWROOM")}
                  className="mt-1 text-primary focus:ring-primary"
                />
                <div className="flex-grow">
                  <div className="mb-1 flex items-center justify-between gap-4">
                    <span className="text-base font-bold">Nhờ showroom làm giấy tờ xe</span>
                    <span className="font-mono text-sm font-bold">+ 2.500.000đ</span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    Showroom hỗ trợ xử lý hồ sơ đăng ký xe. Khoản phí này sẽ được cộng vào tổng thanh toán.
                  </p>
                </div>
              </label>
            </div>
          </section>

          <section className="rounded-xl border border-outline-variant/15 bg-white p-8">
            <h3 className="mb-4 text-xl font-bold">Mã giảm giá / khuyến mãi</h3>
            <div className="grid gap-4 sm:grid-cols-[1fr_auto]">
              <input
                type="text"
                value={discountCode}
                onChange={(event) => {
                  setDiscountCode(event.target.value);
                  setDiscountCodeError(null);
                  setDiscountCodeMessage(null);
                }}
                placeholder="Nhập mã giảm giá"
                className={inputClassName(Boolean(discountCodeError))}
              />
              <button
                type="button"
                onClick={() => {
                  const code = discountCode.trim().toUpperCase();
                  setDiscountCodeError(null);
                  setDiscountCodeMessage(null);
                  setAppliedDiscountCode(code || null);
                }}
                className="inline-flex items-center justify-center rounded-lg border border-outline-variant/20 bg-white px-4 py-3 text-sm font-bold transition hover:border-primary disabled:cursor-not-allowed disabled:opacity-60"
              >
                Áp dụng
              </button>
            </div>
            {discountCodeError ? <p className="mt-3 text-sm text-red-600">{discountCodeError}</p> : null}
            {discountCodeMessage ? <p className="mt-3 text-sm text-green-700">{discountCodeMessage}</p> : null}
            <p className="mt-3 text-xs text-muted-foreground">Bạn có thể thử mã: <span className="font-mono">EBIKE100</span>, <span className="font-mono">EBIKE200</span></p>
          </section>

          <section className="rounded-xl border border-outline-variant/15 bg-white p-8">
            <h3 className="mb-4 text-xl font-bold">Thông tin bổ sung</h3>
            <textarea
              rows={5}
              value={form.notes}
              onChange={(event) => updateForm("notes", event.target.value)}
              placeholder="Ghi chú thêm cho showroom nếu cần."
              className={`w-full rounded-lg border px-4 py-3 text-sm font-medium focus:outline-none ${
                fieldErrors.notes
                  ? "border-red-500 bg-red-50/40 text-red-700 focus:border-red-500"
                  : "border-outline-variant/20 bg-white focus:border-primary"
              }`}
            />
            {fieldErrors.notes ? <p className="mt-2 text-sm text-red-600">{fieldErrors.notes}</p> : null}
          </section>
        </div>

        <div className="lg:col-span-5">
          <div className="sticky top-32 space-y-8">
            <div className="rounded-xl border border-outline-variant/15 bg-white p-8 shadow-sm">
              <div className="mb-8 aspect-[16/9] overflow-hidden rounded-lg bg-surface-container-low">
                <img
                  src={resolveProductImage(product.image) || "https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&q=80&w=800"}
                  alt={product.name}
                  className="h-full w-full object-cover"
                  onError={(event) => attachImageFallback(event, product.name)}
                  referrerPolicy="no-referrer"
                />
              </div>

              <div className="mb-8 flex items-end justify-between">
                <div>
                  <h3 className="text-2xl font-bold tracking-tight">{product.name}</h3>
                  <p className="text-sm text-muted-foreground">Phiên bản: {state.selectedColor || "Mặc định"}</p>
                  <p className="mt-1 text-xs uppercase tracking-wider text-muted-foreground">{product.categoryName || "E-Bike"}</p>
                </div>
                <span className="font-mono text-xl font-bold text-primary">{product.price.toLocaleString("vi-VN")}đ</span>
              </div>

              <div className="space-y-4 border-t border-outline-variant/15 pt-8">
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Số lượng</span>
                  <span className="font-mono font-medium">{quantity}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Tạm tính</span>
                  <span className="font-mono font-medium">{subtotal.toLocaleString("vi-VN")}đ</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Tổng ưu đãi</span>
                  <span className="font-mono font-medium text-error">- {totalDiscount.toLocaleString("vi-VN")}đ</span>
                </div>
                <div className="flex justify-between text-sm"><span className="text-muted-foreground">Mã giảm giá {appliedDiscountCode ? `(${appliedDiscountCode})` : ""}</span><span className="font-mono font-medium text-error">{appliedDiscountCode === "EBIKE200" ? "- 200.000đ" : appliedDiscountCode === "EBIKE100" ? "- 100.000đ" : "0đ"}</span></div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Phí làm giấy tờ xe</span>
                  <span className="font-mono font-medium">{registrationFee.toLocaleString("vi-VN")}đ</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Dịch vụ đăng ký xe</span>
                  <span className="font-medium">{registrationService === "SHOWROOM" ? "Showroom hỗ trợ" : "Khách tự làm"}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Nhận tại showroom</span>
                  <span className="font-mono font-medium text-primary">Miễn phí</span>
                </div>
              </div>

              <div className="mt-8 border-t-2 border-foreground pt-8">
                <div className="mb-8 flex items-baseline justify-between">
                  <span className="text-lg font-bold">Tổng thanh toán</span>
                  <div className="text-right">
                    <span className="text-3xl font-bold tracking-tighter">{total.toLocaleString("vi-VN")}đ</span>
                    <p className="mt-1 text-[10px] font-mono text-muted-foreground">{loadingQuote ? "ĐANG TÍNH LẠI" : "ĐÃ BAO GỒM VAT"}</p>
                  </div>
                </div>
                <button
                  onClick={() => void handleSubmit()}
                  disabled={submitting || emailOtpLoading || loadingQuote || !orderQuote}
                  className="w-full rounded-lg bg-primary py-5 text-lg font-bold tracking-tight text-white transition-all hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {submitting || emailOtpLoading
                    ? selectedPaymentMethod === "VNPAY"
                      ? "Đang chuyển sang VNPay..."
                      : "Đang tạo đơn hàng..."
                    : selectedPaymentMethod === "VNPAY"
                      ? "Thanh toán với VNPay"
                      : "Xác nhận đặt hàng"}
                </button>
                <p className="mt-4 flex items-center justify-center gap-2 text-center text-xs text-muted-foreground">
                  <Lock size={14} />
                  Thanh toán bảo mật được mã hóa
                </p>
              </div>
            </div>

            <div className="flex items-center gap-6 rounded-xl bg-surface-container-low p-6">
              <div className="flex-grow">
                <div className="mb-2 flex items-center gap-2">
                  <Zap size={14} className="text-primary" fill="currentColor" />
                  <span className="mono-label text-primary">CHỈ NHẬN XE TẠI TP.HCM</span>
                </div>
                <p className="text-xs leading-relaxed text-muted-foreground">
                  Hệ thống hiện chỉ hỗ trợ nhận xe tại showroom trong TP.HCM. Khách hàng chọn quận và địa chỉ cụ thể để showroom liên hệ.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CheckoutPage;
