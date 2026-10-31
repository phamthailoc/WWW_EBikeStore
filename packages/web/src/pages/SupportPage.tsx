import { useState } from "react";
import { AlertCircle, Mail, Phone, ShieldCheck, Truck, Zap } from "lucide-react";

const FAQ_ITEMS = [
  {
    question: "Làm sao để đặt hàng?",
    answer:
      "Bạn chỉ cần chọn mẫu xe, thêm vào giỏ hàng và đi đến trang thanh toán. Tại đó hãy kiểm tra lại thông tin đơn hàng và hoàn tất đặt hàng." 
  },
  {
    question: "Thanh toán bằng VNPay có an toàn không?",
    answer:
      "Hệ thống sử dụng VNPay để thanh toán trực tuyến. Nếu phát sinh lỗi, bạn có thể thử lại hoặc liên hệ với bộ phận hỗ trợ để được hướng dẫn." 
  },
  {
    question: "Khi nào đơn hàng được giao?",
    answer:
      "Sau khi xác nhận thanh toán, chúng tôi sẽ xử lý đơn và giao hàng trong vòng 3-5 ngày làm việc. Bạn sẽ nhận được thông tin theo dõi đơn hàng qua email." 
  },
  {
    question: "Làm sao đổi hoặc hủy đơn hàng?",
    answer:
      "Nếu đơn chưa được giao, bạn có thể liên hệ ngay với chúng tôi qua form dưới đây để yêu cầu hủy hoặc thay đổi thông tin." 
  },
  {
    question: "Hỗ trợ bảo hành và kỹ thuật?",
    answer:
      "Bạn có thể gửi yêu cầu hỗ trợ kỹ thuật hoặc bảo hành tại đây. Nhóm chăm sóc khách hàng sẽ phản hồi trong vòng 24 giờ làm việc." 
  }
];

const validateEmail = (email: string) => {
  const normalized = email.trim();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized);
};

const SupportPage = () => {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};

    if (!name.trim()) nextErrors.name = "Vui lòng nhập tên của bạn.";
    if (!email.trim()) {
      nextErrors.email = "Vui lòng nhập email.";
    } else if (!validateEmail(email)) {
      nextErrors.email = "Email không hợp lệ.";
    }
    if (!subject.trim()) nextErrors.subject = "Vui lòng nhập chủ đề hỗ trợ.";
    if (!message.trim()) nextErrors.message = "Vui lòng nhập nội dung yêu cầu.";

    setErrors(nextErrors);

    if (Object.keys(nextErrors).length === 0) {
      setSubmitted(true);
      setName("");
      setEmail("");
      setSubject("");
      setMessage("");
      setTimeout(() => setSubmitted(false), 5000);
    }
  };

  return (
    <div className="px-6 pb-20 pt-24 md:px-12">
      <section className="mx-auto max-w-6xl">
        <div className="rounded-[2rem] bg-surface-container-low p-10 shadow-lg shadow-black/5">
          <div className="mb-10 grid gap-8 lg:grid-cols-[1.2fr_0.8fr] lg:items-center">
            <div>
              <p className="mono-label mb-4 text-primary">Hỗ trợ khách hàng</p>
              <h1 className="mb-4 text-4xl font-bold leading-tight">Chúng tôi luôn sẵn sàng giúp bạn</h1>
              <p className="max-w-2xl text-base leading-relaxed text-muted-foreground">
                Nếu bạn cần trợ giúp về đặt hàng, thanh toán, giao hàng hoặc bảo hành, hãy gửi yêu cầu dưới đây. 
                Nhóm hỗ trợ sẽ phản hồi bạn càng sớm càng tốt.
              </p>
            </div>
            <div className="grid gap-4">
              <div className="rounded-3xl border border-outline-variant/70 bg-background p-6">
                <div className="flex items-center gap-3 text-primary">
                  <Phone size={22} />
                  <span className="text-lg font-semibold">SĐT hỗ trợ</span>
                </div>
                <p className="mt-3 text-sm text-foreground/80">1900 1234</p>
                <p className="mt-1 text-sm text-muted-foreground">8:00 - 20:00 mỗi ngày</p>
              </div>
              <div className="rounded-3xl border border-outline-variant/70 bg-background p-6">
                <div className="flex items-center gap-3 text-primary">
                  <Mail size={22} />
                  <span className="text-lg font-semibold">Email hỗ trợ</span>
                </div>
                <p className="mt-3 text-sm text-foreground/80">support@kinetic.vn</p>
                <p className="mt-1 text-sm text-muted-foreground">Phản hồi trong vòng 24 giờ làm việc</p>
              </div>
            </div>
          </div>

          <div className="grid gap-8 lg:grid-cols-[1.3fr_0.9fr]">
            <div className="space-y-8">
              <div className="rounded-[1.5rem] bg-background p-8">
                <h2 className="mb-6 text-3xl font-bold">Các câu hỏi thường gặp</h2>
                <div className="space-y-6">
                  {FAQ_ITEMS.map((item) => (
                    <div key={item.question} className="rounded-3xl border border-outline-variant/70 bg-surface-container-lowest p-6">
                      <h3 className="mb-2 text-lg font-semibold">{item.question}</h3>
                      <p className="text-sm leading-relaxed text-muted-foreground">{item.answer}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="rounded-[1.5rem] bg-primary/5 p-8">
              <div className="mb-6 rounded-3xl bg-background p-6 shadow-sm">
                <h2 className="text-3xl font-bold">Gửi yêu cầu hỗ trợ</h2>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                  Điền thông tin vào form dưới đây để yêu cầu hỗ trợ. Chúng tôi sẽ liên hệ lại với bạn qua email.
                </p>
              </div>

              {submitted ? (
                <div className="rounded-3xl border border-green-300 bg-green-50 p-6 text-green-900">
                  <div className="mb-2 flex items-center gap-2 font-semibold">
                    <ShieldCheck size={18} />
                    Yêu cầu đã gửi
                  </div>
                  <p className="text-sm leading-relaxed">
                    Cảm ơn bạn! Chúng tôi đã nhận được yêu cầu và sẽ phản hồi trong vòng 24 giờ làm việc.
                  </p>
                </div>
              ) : null}

              <form className="space-y-5" onSubmit={handleSubmit} noValidate>
                <label className="block text-sm font-semibold text-foreground/90">
                  Họ tên
                  <input
                    type="text"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    className="mt-2 w-full rounded-3xl border border-outline-variant/70 bg-background px-4 py-3 text-sm text-foreground outline-none transition-shadow focus:border-primary focus:shadow-sm"
                    placeholder="Nhập họ tên của bạn"
                  />
                  {errors.name ? <p className="mt-2 text-xs text-red-500">{errors.name}</p> : null}
                </label>

                <label className="block text-sm font-semibold text-foreground/90">
                  Email
                  <input
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    className="mt-2 w-full rounded-3xl border border-outline-variant/70 bg-background px-4 py-3 text-sm text-foreground outline-none transition-shadow focus:border-primary focus:shadow-sm"
                    placeholder="example@domain.com"
                  />
                  {errors.email ? <p className="mt-2 text-xs text-red-500">{errors.email}</p> : null}
                </label>

                <label className="block text-sm font-semibold text-foreground/90">
                  Chủ đề
                  <input
                    type="text"
                    value={subject}
                    onChange={(event) => setSubject(event.target.value)}
                    className="mt-2 w-full rounded-3xl border border-outline-variant/70 bg-background px-4 py-3 text-sm text-foreground outline-none transition-shadow focus:border-primary focus:shadow-sm"
                    placeholder="Vấn đề đặt hàng / thanh toán / bảo hành"
                  />
                  {errors.subject ? <p className="mt-2 text-xs text-red-500">{errors.subject}</p> : null}
                </label>

                <label className="block text-sm font-semibold text-foreground/90">
                  Nội dung yêu cầu
                  <textarea
                    value={message}
                    onChange={(event) => setMessage(event.target.value)}
                    rows={6}
                    className="mt-2 w-full rounded-3xl border border-outline-variant/70 bg-background px-4 py-3 text-sm text-foreground outline-none transition-shadow focus:border-primary focus:shadow-sm"
                    placeholder="Mô tả chi tiết về vấn đề bạn cần hỗ trợ"
                  />
                  {errors.message ? <p className="mt-2 text-xs text-red-500">{errors.message}</p> : null}
                </label>

                <button
                  type="submit"
                  className="inline-flex w-full items-center justify-center rounded-3xl bg-primary px-5 py-3 text-sm font-semibold text-white transition hover:bg-primary/90"
                >
                  Gửi yêu cầu hỗ trợ
                </button>
              </form>
            </div>
          </div>
        </div>
      </section>

      <section className="mt-14 mx-auto max-w-6xl grid gap-6 lg:grid-cols-3">
        <div className="rounded-[1.5rem] bg-surface-container-lowest p-8">
          <div className="mb-4 inline-flex items-center gap-3 rounded-3xl bg-primary/10 px-4 py-3 text-primary">
            <AlertCircle size={18} />
            <span className="font-semibold">Nội dung hỗ trợ phổ biến</span>
          </div>
          <p className="text-sm leading-relaxed text-muted-foreground">
            Bạn có thể gửi yêu cầu về đặt hàng, giao nhận, thanh toán, bảo hành và kỹ thuật. Chúng tôi sẽ phản hồi chính xác theo yêu cầu của bạn.
          </p>
        </div>

        <div className="rounded-[1.5rem] bg-surface-container-lowest p-8">
          <div className="mb-4 inline-flex items-center gap-3 rounded-3xl bg-primary/10 px-4 py-3 text-primary">
            <ShieldCheck size={18} />
            <span className="font-semibold">Cam kết chất lượng</span>
          </div>
          <p className="text-sm leading-relaxed text-muted-foreground">
            Mọi yêu cầu sẽ được xử lý cẩn thận với tiêu chí giải quyết nhanh chóng và minh bạch.
          </p>
        </div>

        <div className="rounded-[1.5rem] bg-surface-container-lowest p-8">
          <div className="mb-4 inline-flex items-center gap-3 rounded-3xl bg-primary/10 px-4 py-3 text-primary">
            <Truck size={18} />
            <span className="font-semibold">Hỗ trợ giao hàng</span>
          </div>
          <p className="text-sm leading-relaxed text-muted-foreground">
            Nếu bạn cần giúp điều chỉnh lịch giao hàng hoặc theo dõi đơn vị vận chuyển, hãy cho chúng tôi biết trong nội dung yêu cầu.
          </p>
        </div>
      </section>
    </div>
  );
};

export default SupportPage;
