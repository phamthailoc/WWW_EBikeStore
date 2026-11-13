package com.ebike.controller.web;

import com.ebike.config.order.VnPayProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkspaceController {

    /** When on, checkout asks the buyer to confirm their email with a code before ordering (UC-06). */
    @org.springframework.beans.factory.annotation.Value("${app.order.email-otp.required:false}")
    private boolean emailOtpRequired;

    private record Link(String label, String href) {}

    private final VnPayProperties vnPayProperties;
    private final org.springframework.context.MessageSource messages;

    public WorkspaceController(VnPayProperties vnPayProperties, org.springframework.context.MessageSource messages) {
        this.vnPayProperties = vnPayProperties;
        this.messages = messages;
    }

    /** The text for a key in the page language; staff pages have no English yet and keep the Vietnamese default. */
    private String m(String key, String defaultText) {
        return messages.getMessage(key, null, defaultText, org.springframework.context.i18n.LocaleContextHolder.getLocale());
    }

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
            case "customer" -> { title = m("page.customer", "Tổng quan tài khoản"); api = "/api/v1/orders"; description = m("page.customerDesc", "Đơn hàng và hoạt động gần đây của bạn."); mode = "dashboard"; }
            case "customer/orders" -> { title = m("page.orders", "Đơn hàng của tôi"); api = "/api/v1/orders"; description = m("page.ordersDesc", "Theo dõi trạng thái và thông tin giao nhận."); }
            case "customer/payments" -> { title = m("page.payments", "Lịch sử thanh toán"); api = "/api/v1/payments/history"; description = m("page.paymentsDesc", "Các giao dịch thanh toán của bạn."); }
            case "customer/notifications" -> { title = m("page.notifications", "Thông báo"); api = "/api/v1/notifications"; description = m("page.notificationsDesc", "Cập nhật về đơn hàng và tài khoản."); }
            case "customer/profile" -> { title = m("page.profile", "Hồ sơ cá nhân"); api = "/api/v1/auth/profile"; description = m("page.profileDesc", "Thông tin liên hệ và tùy chọn tài khoản."); mode = "profile"; }
            case "manager" -> { title = m("staff.1", "Tổng quan cửa hàng"); api = "/api/v1/manager/dashboard"; description = m("staff.2", "Đơn hàng, thanh toán và doanh thu cửa hàng."); mode = "dashboard"; }
            case "manager/orders" -> { title = m("staff.3", "Quản lý đơn hàng"); api = "/api/v1/orders"; description = m("staff.4", "Theo dõi và cập nhật các đơn xe."); }
            case "manager/payments" -> { title = m("staff.5", "Thanh toán"); api = "/api/v1/manager/payments"; description = m("staff.6", "Xác nhận giao dịch và thanh toán sau."); }
            case "manager/support" -> { title = m("staff.7", "Yêu cầu hỗ trợ"); api = "/api/v1/manager/support-requests"; description = m("staff.8", "Câu hỏi và khiếu nại khách gửi từ trang Hỗ trợ."); }
            case "manager/customers" -> { title = m("staff.9", "Khách hàng"); api = "/api/v1/manager/customers"; description = m("staff.10", "Danh sách khách hàng và lịch sử mua."); }
            case "manager/inventory", "manager/products", "admin/inventory", "admin/products" -> { title = m("staff.11", "Kho sản phẩm"); api = "/api/v1/products"; description = m("staff.12", "Theo dõi tồn kho và tình trạng xe."); mode = "inventory"; }
            case "manager/reports" -> { title = m("staff.13", "Báo cáo doanh thu"); api = "/api/v1/manager/revenue-report"; description = m("staff.14", "Tổng hợp hoạt động kinh doanh."); mode = "dashboard"; }
            case "admin" -> { title = m("staff.15", "Bảng điều khiển quản trị"); api = "/api/v1/admin/overview"; description = m("staff.16", "Tài khoản, chương trình giá và hoạt động hệ thống."); mode = "dashboard"; }
            case "admin/pricing" -> { title = m("staff.17", "Giá và khuyến mãi"); api = "/api/v1/admin/pricing-rules"; description = m("staff.18", "Quản lý phí dịch vụ và chiến dịch ưu đãi."); mode = "pricing"; }
            case "admin/accounts" -> { title = m("staff.19", "Tài khoản và phân quyền"); api = "/api/v1/admin/accounts"; description = m("staff.20", "Quản lý tài khoản và vai trò truy cập."); mode = "accounts"; }
            case "admin/roles" -> { title = m("staff.21", "Vai trò và quyền"); api = "/api/v1/admin/roles"; description = m("staff.22", "Mỗi vai trò được làm những gì trong hệ thống."); }
            case "admin/audit-logs" -> { title = m("staff.23", "Nhật ký hoạt động"); api = "/api/v1/admin/audit-logs"; description = m("staff.24", "100 thao tác quản trị gần nhất."); }
            case "admin/users" -> { title = m("staff.25", "Người dùng"); api = "/api/v1/admin/accounts?type=CUSTOMER"; description = m("staff.26", "Danh sách tài khoản khách hàng."); }
            default -> {
                if (path.startsWith("/customer/orders/")) { title = m("page.orderDetail", "Chi tiết đơn hàng"); api = "/api/v1/orders/" + id; description = m("page.orderDetailDesc", "Chi tiết và tiến trình đơn hàng."); mode = "detail"; }
                else if (path.startsWith("/manager/orders/")) { title = m("staff.27", "Chi tiết đơn hàng"); api = "/api/v1/orders/" + id; description = m("staff.28", "Cập nhật đơn hàng và giao nhận."); mode = "detail"; }
                else { title = m("page.notFound", "Không tìm thấy trang"); api = ""; description = m("page.notFoundDesc", "Đường dẫn không tồn tại."); }
            }
        }
        List<Link> links = switch (role) {
            case "admin" -> List.of(new Link(m("staff.29", "Tổng quan"), "/admin"), new Link(m("staff.30", "Sản phẩm"), "/admin/products"), new Link(m("staff.31", "Kho hàng"), "/admin/inventory"), new Link(m("staff.32", "Giá & khuyến mãi"), "/admin/pricing"), new Link(m("staff.33", "Tài khoản"), "/admin/accounts"), new Link(m("staff.34", "Vai trò"), "/admin/roles"), new Link(m("staff.35", "Nhật ký"), "/admin/audit-logs"), new Link(m("staff.25", "Người dùng"), "/admin/users"));
            case "manager" -> List.of(new Link(m("staff.29", "Tổng quan"), "/manager"), new Link(m("staff.36", "Đơn hàng"), "/manager/orders"), new Link(m("staff.5", "Thanh toán"), "/manager/payments"), new Link(m("staff.9", "Khách hàng"), "/manager/customers"), new Link(m("staff.37", "Hỗ trợ"), "/manager/support"), new Link(m("staff.31", "Kho hàng"), "/manager/inventory"), new Link(m("staff.30", "Sản phẩm"), "/manager/products"), new Link(m("staff.38", "Báo cáo"), "/manager/reports"));
            default -> List.of(new Link(m("menu.overview", "Tổng quan"), "/customer"), new Link(m("menu.orders", "Đơn hàng"), "/customer/orders"), new Link(m("menu.payments", "Thanh toán"), "/customer/payments"), new Link(m("menu.notifications", "Thông báo"), "/customer/notifications"), new Link(m("menu.profile", "Hồ sơ"), "/customer/profile"), new Link(m("menu.favourites", "Yêu thích"), "/favorites"));
        };
        model.addAttribute("workspaceRole", role);
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("api", api);
        model.addAttribute("secondaryApi", section.equals("admin/pricing") ? "/api/v1/admin/promotions" : "");
        model.addAttribute("mode", mode);
        model.addAttribute("links", links);
        model.addAttribute("activePath", path);
        model.addAttribute("vnpayEnabled", vnPayProperties.isConfigured());
        return "store/workspace";
    }

    @GetMapping({"/cart", "/favorites", "/checkout", "/checkout/success", "/payment/return", "/support", "/compare", "/chatbot"})
    public String customerFlow(HttpServletRequest request, Model model) {
        String path = request.getRequestURI();
        String title = switch (path) {
            case "/cart" -> m("flow.cart", "Giỏ hàng của bạn");
            case "/favorites" -> m("flow.favourites", "Sản phẩm yêu thích");
            case "/checkout" -> m("flow.checkout", "Thanh toán đơn hàng");
            case "/checkout/success" -> m("flow.success", "Đặt hàng thành công");
            case "/payment/return" -> m("flow.paymentResult", "Kết quả thanh toán");
            case "/support" -> m("flow.support", "Hỗ trợ khách hàng");
            case "/compare" -> m("flow.compare", "So sánh xe");
            default -> m("flow.assistant", "Trợ lý KINETIC");
        };
        String api = switch (path) {
            case "/favorites" -> "/api/v1/favorites";
            case "/support" -> "/api/v1/showrooms";
            default -> "";
        };
        model.addAttribute("title", title);
        model.addAttribute("api", api);
        model.addAttribute("flow", path.substring(1).replace('/', '-'));
        model.addAttribute("vnpayEnabled", vnPayProperties.isConfigured());
        model.addAttribute("emailOtpRequired", emailOtpRequired);
        return "store/flow";
    }
}

