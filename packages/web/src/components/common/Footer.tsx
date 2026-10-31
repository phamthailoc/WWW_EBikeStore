import { Globe, Mail, Share2 } from "lucide-react";
import { Link, useLocation } from "react-router-dom";
import { useAuth } from "@ebike/shared-code/hooks";

const Footer = () => {
  const location = useLocation();
  const { isAuthenticated } = useAuth();

  if (location.pathname.startsWith("/checkout") || location.pathname.startsWith("/chatbot")) {
    return null;
  }

  return (
    <footer className="mt-auto w-full border-t border-outline-variant/10 bg-surface-container-low px-6 py-20 md:px-12">
      <div className="mx-auto max-w-7xl">
        <div className="mb-16 grid grid-cols-2 gap-12 md:grid-cols-4">
          <div className="col-span-2 space-y-6 md:col-span-1">
            <div className="font-headline text-xl font-bold uppercase text-foreground">KINETIC</div>
            <p className="max-w-xs text-sm leading-relaxed text-muted-foreground">
              Đồng hành cùng xu hướng di chuyển đô thị bền vững bằng kỹ thuật chính xác và thiết kế hiện đại.
            </p>
            <div className="flex gap-4 text-muted-foreground">
              <Globe size={20} className="cursor-pointer transition-colors hover:text-primary" />
              <Share2 size={20} className="cursor-pointer transition-colors hover:text-primary" />
              <Mail size={20} className="cursor-pointer transition-colors hover:text-primary" />
            </div>
          </div>

          <div>
            <h4 className="mono-label mb-6 text-foreground/40">Khám phá</h4>
            <ul className="space-y-3">
              <li><Link to="/products" className="text-sm text-muted-foreground transition-colors hover:text-primary">Mẫu xe</Link></li>
              <li><Link to="/chatbot" className="text-sm text-muted-foreground transition-colors hover:text-primary">Tư vấn</Link></li>
              {isAuthenticated ? (
                <li><Link to="/favorites" className="text-sm text-muted-foreground transition-colors hover:text-primary">Đã lưu</Link></li>
              ) : null}
            </ul>
          </div>

          <div>
            <h4 className="mono-label mb-6 text-foreground/40">Công ty</h4>
            <ul className="space-y-3">
              <li><Link to="/" className="text-sm text-muted-foreground transition-colors hover:text-primary">Về Kinetic</Link></li>
              <li><Link to="/support" className="text-sm text-muted-foreground transition-colors hover:text-primary">Hỗ trợ</Link></li>
              <li><Link to="/customer/orders" className="text-sm text-muted-foreground transition-colors hover:text-primary">Đơn hàng</Link></li>
              <li><Link to="/auth" className="text-sm text-muted-foreground transition-colors hover:text-primary">Tài khoản</Link></li>
            </ul>
          </div>

          <div>
            <h4 className="mono-label mb-6 text-foreground/40">Pháp lý</h4>
            <ul className="space-y-3">
              <li><Link to="/" className="text-sm text-muted-foreground transition-colors hover:text-primary">Chính sách bảo mật</Link></li>
              <li><Link to="/" className="text-sm text-muted-foreground transition-colors hover:text-primary">Điều khoản dịch vụ</Link></li>
            </ul>
          </div>
        </div>

        <div className="flex flex-col items-center justify-between gap-6 border-t border-outline-variant/10 pt-12 md:flex-row">
          <p className="text-sm text-muted-foreground">© 2026 Kinetic Precision. Bảo lưu mọi quyền.</p>
          <div className="flex gap-8 text-xs uppercase tracking-widest text-muted-foreground">
            <span>Văn phòng Việt Nam: TP.HCM, Việt Nam</span>
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
