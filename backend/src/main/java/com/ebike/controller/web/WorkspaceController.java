package com.ebike.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkspaceController {
    private record Link(String label, String href) {}

    @GetMapping({"/customer", "/customer/**", "/manager", "/manager/**", "/admin", "/admin/**"})
    public String workspace(HttpServletRequest request, Model model) {
        String path = request.getRequestURI();
        String role = path.startsWith("/admin") ? "admin" : path.startsWith("/manager") ? "manager" : "customer";
        String section = path.substring(1);
        String id = path.substring(path.lastIndexOf('/') + 1);
        String title;
        String api;
        String description;
        String mode = "table";
        switch (section) {
            case "customer" -> { title = "Tổng quan tài khoản"; api = "/api/v1/orders"; description = "Đơn hàng và hoạt động gần đây của bạn."; mode = "dashboard"; }
            case "customer/orders" -> { title = "Đơn hàng của tôi"; api = "/api/v1/orders"; description = "Theo dõi trạng thái và thông tin giao nhận."; }
            case "customer/payments" -> { title = "Lịch sử thanh toán"; api = "/api/v1/payments/history"; description = "Các giao dịch thanh toán của bạn."; }
            case "customer/notifications" -> { title = "Thông báo"; api = "/api/v1/notifications"; description = "Cập nhật về đơn hàng và tài khoản."; }
            case "customer/profile" -> { title = "Hồ sơ cá nhân"; api = "/api/v1/auth/profile"; description = "Thông tin liên hệ và tùy chọn tài khoản."; mode = "profile"; }
            case "manager" -> { title = "Tổng quan cửa hàng"; api = "/api/v1/manager/dashboard"; description = "Đơn hàng, thanh toán và doanh thu cửa hàng."; mode = "dashboard"; }
            case "manager/orders" -> { title = "Quản lý đơn hàng"; api = "/api/v1/orders"; description = "Theo dõi và cập nhật các đơn xe."; }
            case "manager/payments" -> { title = "Thanh toán"; api = "/api/v1/manager/payments"; description = "Xác nhận giao dịch và thanh toán sau."; }
            case "manager/customers" -> { title = "Khách hàng"; api = "/api/v1/manager/customers"; description = "Danh sách khách hàng và lịch sử mua."; }
            case "manager/inventory", "manager/products", "admin/inventory", "admin/products" -> { title = "Kho sản phẩm"; api = "/api/v1/products"; description = "Theo dõi tồn kho và tình trạng xe."; mode = "inventory"; }
            case "manager/reports" -> { title = "Báo cáo doanh thu"; api = "/api/v1/manager/revenue-report"; description = "Tổng hợp hoạt động kinh doanh."; mode = "dashboard"; }
            case "admin" -> { title = "Bảng điều khiển quản trị"; api = "/api/v1/admin/overview"; description = "Tài khoản, chương trình giá và hoạt động hệ thống."; mode = "dashboard"; }
            case "admin/pricing" -> { title = "Giá và khuyến mãi"; api = "/api/v1/admin/pricing-rules"; description = "Quản lý phí dịch vụ và chiến dịch ưu đãi."; mode = "pricing"; }
            case "admin/accounts" -> { title = "Tài khoản và phân quyền"; api = "/api/v1/admin/accounts"; description = "Quản lý tài khoản và vai trò truy cập."; mode = "accounts"; }
            case "admin/users" -> { title = "Người dùng"; api = "/api/v1/admin/accounts?type=CUSTOMER"; description = "Danh sách tài khoản khách hàng."; }
            default -> {
                if (path.startsWith("/customer/orders/")) { title = "Chi tiết đơn hàng"; api = "/api/v1/orders/" + id; description = "Chi tiết và tiến trình đơn hàng."; mode = "detail"; }
                else if (path.startsWith("/manager/orders/")) { title = "Chi tiết đơn hàng"; api = "/api/v1/orders/" + id; description = "Cập nhật đơn hàng và giao nhận."; mode = "detail"; }
                else { title = "Không tìm thấy trang"; api = ""; description = "Đường dẫn không tồn tại."; }
            }
        }
        List<Link> links = switch (role) {
            case "admin" -> List.of(new Link("Tổng quan", "/admin"), new Link("Sản phẩm", "/admin/products"), new Link("Kho hàng", "/admin/inventory"), new Link("Giá & khuyến mãi", "/admin/pricing"), new Link("Tài khoản", "/admin/accounts"), new Link("Người dùng", "/admin/users"));
            case "manager" -> List.of(new Link("Tổng quan", "/manager"), new Link("Đơn hàng", "/manager/orders"), new Link("Thanh toán", "/manager/payments"), new Link("Khách hàng", "/manager/customers"), new Link("Kho hàng", "/manager/inventory"), new Link("Sản phẩm", "/manager/products"), new Link("Báo cáo", "/manager/reports"));
            default -> List.of(new Link("Tổng quan", "/customer"), new Link("Đơn hàng", "/customer/orders"), new Link("Thanh toán", "/customer/payments"), new Link("Thông báo", "/customer/notifications"), new Link("Hồ sơ", "/customer/profile"), new Link("Yêu thích", "/favorites"));
        };
        model.addAttribute("workspaceRole", role);
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("api", api);
        model.addAttribute("secondaryApi", section.equals("admin/pricing") ? "/api/v1/admin/promotions" : "");
        model.addAttribute("mode", mode);
        model.addAttribute("links", links);
        model.addAttribute("activePath", path);
        return "store/workspace";
    }

    @GetMapping({"/cart", "/favorites", "/checkout", "/checkout/success", "/payment/return", "/support", "/compare", "/chatbot"})
    public String customerFlow(HttpServletRequest request, Model model) {
        String path = request.getRequestURI();
        String title = switch (path) {
            case "/cart" -> "Giỏ hàng của bạn";
            case "/favorites" -> "Sản phẩm yêu thích";
            case "/checkout" -> "Thanh toán đơn hàng";
            case "/checkout/success" -> "Đặt hàng thành công";
            case "/payment/return" -> "Kết quả thanh toán";
            case "/support" -> "Hỗ trợ khách hàng";
            case "/compare" -> "So sánh xe";
            default -> "Trợ lý KINETIC";
        };
        String api = switch (path) {
            case "/favorites" -> "/api/v1/favorites";
            case "/support" -> "/api/v1/showrooms";
            default -> "";
        };
        model.addAttribute("title", title);
        model.addAttribute("api", api);
        model.addAttribute("flow", path.substring(1).replace('/', '-'));
        return "store/flow";
    }
}

