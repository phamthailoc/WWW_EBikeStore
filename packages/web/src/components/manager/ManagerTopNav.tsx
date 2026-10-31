import { Search } from "lucide-react";
import { useLocation } from "react-router-dom";
import ManagerNotificationsBell from "./ManagerNotificationsBell";

const pageTitles: Record<string, { title: string; description: string }> = {
  "/manager": {
    title: "Tổng quan quản lý",
    description: "Theo dõi nhanh tình hình đơn hàng, thanh toán và khách hàng."
  },
  "/manager/orders": {
    title: "Quản lý đơn hàng",
    description: "Theo dõi và xử lý toàn bộ đơn hàng của khách."
  },
  "/manager/payments": {
    title: "Quản lý thanh toán",
    description: "Đối soát và xác nhận các giao dịch thanh toán."
  },
  "/manager/customers": {
    title: "Khách hàng",
    description: "Tra cứu hồ sơ khách hàng và giá trị mua sắm."
  },
  "/manager/reports": {
    title: "Báo cáo doanh thu",
    description: "Theo dõi doanh thu, đơn hàng và sản phẩm bán chạy theo thời gian."
  },
  "/manager/products": {
    title: "Quản lý tồn kho",
    description: "Theo dõi số lượng, cảnh báo sắp hết hàng và cập nhật tồn kho sản phẩm."
  }
};

const managerPageTitles: Record<string, { title: string; description: string }> = {
  ...pageTitles,
  "/manager/products": {
    title: "Quản lý sản phẩm",
    description: "Kiểm tra danh mục xe đang hiển thị trong hệ thống."
  },
  "/manager/inventory": {
    title: "Quản lý tồn kho",
    description: "Theo dõi số lượng, cảnh báo sắp hết hàng và cập nhật tồn kho sản phẩm."
  }
};

const ManagerTopNav = () => {
  const location = useLocation();
  const page =
    managerPageTitles[location.pathname] ??
    (location.pathname.startsWith("/manager/orders/")
      ? {
          title: "Chi tiết đơn hàng",
          description: "Xem đầy đủ thông tin đơn hàng, thanh toán và showroom nhận xe."
        }
      : managerPageTitles["/manager"]);

  return (
    <header className="sticky top-0 z-30 flex min-h-[72px] items-center justify-between border-b border-slate-200 bg-white/95 px-6 backdrop-blur">
      <div>
        <h1 className="text-[28px] font-bold text-slate-950">{page.title}</h1>
        <p className="mt-1 text-sm text-slate-500">{page.description}</p>
      </div>

      <div className="hidden items-center justify-end gap-3 md:flex">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Tìm đơn hàng, khách hàng, thanh toán"
            className="w-[360px] rounded-lg border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-[#003b93] focus:bg-white"
          />
        </div>
        <ManagerNotificationsBell />
      </div>
    </header>
  );
};

export default ManagerTopNav;
