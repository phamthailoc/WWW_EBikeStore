package com.ebike.service.installment;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.common.i18n.RequestLanguage;
import com.ebike.dto.installment.InstallmentApplicationResponse;
import com.ebike.dto.installment.InstallmentBacklogResponse;
import com.ebike.dto.installment.InstallmentDecisionRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.event.notification.InstallmentNoticeEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.order.OrderService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * V24: reading instalment applications (a customer sees the one of their own order, the staff see the queue) and the staff's verdict.
 * Approving lets the customer pay the down payment; rejecting cancels the order, so its stock goes back on sale.
 */
@Service
public class InstallmentService {

    /** The most rows the staff queue returns, so a long backlog cannot make the page heavy; the page says when the list is full. */
    public static final int QUEUE_LIMIT = 200;
    private static final int MAX_NOTE_LENGTH = 1000;
    private static final Set<String> QUEUE_STATUSES = Set.of(
        InstallmentApplication.PENDING, InstallmentApplication.APPROVED, InstallmentApplication.REJECTED, InstallmentApplication.CANCELLED);

    private final InstallmentApplicationRepository applications;
    private final UserRepository users;
    private final OrderService orders;
    private final OrderRepository orderRepository;
    private final AuditLogService auditLog;
    // how long an approved application waits for its down payment before the order is cancelled (the expiry job reads the same setting)
    private final Duration downPaymentHold;

    public InstallmentService(
        InstallmentApplicationRepository applications,
        UserRepository users,
        OrderService orders,
        OrderRepository orderRepository,
        AuditLogService auditLog,
        @Value("${app.order.installment-down-payment-hold:PT72H}") Duration downPaymentHold
    ) {
        this.applications = applications;
        this.users = users;
        this.orders = orders;
        this.orderRepository = orderRepository;
        this.auditLog = auditLog;
        this.downPaymentHold = downPaymentHold;
    }

    /** The application of one order, for its customer or for the staff; 404 when the order has none or is somebody else's (so it cannot be probed). */
    @Transactional(readOnly = true)
    public InstallmentApplicationResponse forOrder(Long orderId, Authentication authentication) {
        InstallmentApplication application = applications.findByOrderId(orderId).orElseThrow(InstallmentService::notFound);
        boolean staff = isBackOffice(authentication);
        if (!staff) {
            User current = currentUser(authentication);
            if (current == null || !current.getId().equals(application.getUser().getId())) {
                throw notFound();
            }
        }
        return toResponse(application, staff);
    }

    /** The queue: {@code status} is PENDING (the default), ALL, or one of the other statuses. Oldest waiting first; a closed list shows the newest first. */
    @Transactional(readOnly = true)
    public List<InstallmentApplicationResponse> queue(String status) {
        String wanted = status == null || status.isBlank() ? InstallmentApplication.PENDING : status.trim().toUpperCase(Locale.ROOT);
        List<InstallmentApplication> rows;
        if ("ALL".equals(wanted)) {
            rows = applications.findAllByOrderByCreatedAtDesc(PageRequest.of(0, QUEUE_LIMIT));
        } else if (QUEUE_STATUSES.contains(wanted)) {
            rows = InstallmentApplication.PENDING.equals(wanted)
                ? applications.findByStatusOrderByCreatedAtAsc(wanted, PageRequest.of(0, QUEUE_LIMIT))
                : applications.findByStatusOrderByCreatedAtDesc(wanted, PageRequest.of(0, QUEUE_LIMIT));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, RequestLanguage.text("Trạng thái không hợp lệ.", "The status is not valid."));
        }
        return rows.stream().map(row -> toResponse(row, true)).toList();
    }

    @Transactional(readOnly = true)
    public InstallmentBacklogResponse backlog() {
        return new InstallmentBacklogResponse(applications.countByStatus(InstallmentApplication.PENDING));
    }

    /** The staff's verdict. A rejection cancels the order (with the reason), an approval opens the down payment. */
    @Transactional
    public InstallmentApplicationResponse decide(Long id, InstallmentDecisionRequest request, Authentication authentication) {
        String decision = request == null || request.decision() == null ? "" : request.decision().trim().toUpperCase(Locale.ROOT);
        boolean approve = "APPROVE".equals(decision);
        if (!approve && !"REJECT".equals(decision)) {
            throw bad("Quyết định phải là APPROVE hoặc REJECT.", "The decision must be APPROVE or REJECT.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw bad("Ghi chú tối đa " + MAX_NOTE_LENGTH + " ký tự.", "A note can have at most " + MAX_NOTE_LENGTH + " characters.");
        }
        if (!approve && note == null) {
            throw bad("Vui lòng nhập lý do từ chối (khách sẽ thấy).", "Please give the reason for rejecting (the customer will see it).");
        }
        // The locks, always in this order (every cancellation of an order takes them the same way): the order row first, then the application row. So an
        // approval and a rejection pressed at once, or a cancellation of the order, wait for each other and then see what the winner did.
        Long orderId = applications.findOrderIdById(id).orElseThrow(InstallmentService::notFound);
        Order order = orderRepository.findLockedById(orderId).orElseThrow(InstallmentService::notFound);
        InstallmentApplication application = applications.findLockedById(id).orElseThrow(InstallmentService::notFound);
        if (!InstallmentApplication.PENDING.equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, RequestLanguage.text(
                "Hồ sơ này đã được xử lý hoặc đơn đã bị hủy. Hãy tải lại danh sách.",
                "This application was already decided or its order was cancelled. Please reload the list."));
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, RequestLanguage.text(
                "Đơn hàng không còn ở trạng thái chờ xác nhận nên không duyệt được hồ sơ.",
                "The order is no longer waiting for confirmation, so the application cannot be decided."));
        }
        if (AccountErasure.DELETED.equals(application.getUser().getAccountStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, RequestLanguage.text(
                "Tài khoản của khách đã bị xóa, không duyệt được hồ sơ.", "The customer's account was deleted, so the application cannot be approved."));
        }

        LocalDateTime now = LocalDateTime.now();
        application.setReviewNote(note);
        application.setReviewedBy(currentUser(authentication));
        application.setReviewedAt(now);
        application.setStatus(approve ? InstallmentApplication.APPROVED : InstallmentApplication.REJECTED);
        applications.saveAndFlush(application);
        if (!approve) {
            // the order goes back with its stock; the closing of the open application skips a rejected one, so the verdict stays
            orders.updateOrderStatus(order.getId(), new OrderStatusUpdateRequest("CANCELLED", "Hồ sơ trả góp bị từ chối: " + note), authentication);
        }
        auditLog.record(approve ? "duyệt hồ sơ trả góp" : "từ chối hồ sơ trả góp", order.getOrderNumber());
        NotificationEventBridge.publish(new InstallmentNoticeEvent(
            application.getUser().getId(), order.getId(), order.getOrderNumber(),
            approve ? InstallmentMessages.APPROVED : InstallmentMessages.REJECTED,
            application.getDownPaymentAmount(), application.getMonthlyPayment(), application.getTermMonths(),
            approve ? now.plus(downPaymentHold) : null, note));
        return toResponse(application, true);
    }

    private InstallmentApplicationResponse toResponse(InstallmentApplication a, boolean forStaff) {
        Order order = a.getOrder();
        User user = a.getUser();
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (AccountErasure.DELETED.equals(user.getAccountStatus())) {
            // the erasure wrote its Vietnamese placeholder into the name: the page shows it in the language of the request instead
            name = RequestLanguage.text("Tài khoản đã xóa", "Deleted account");
        }
        Payment payment = order.getPayment();
        boolean orderOpen = order.getStatus() == OrderStatus.PENDING;
        boolean approved = InstallmentApplication.APPROVED.equals(a.getStatus());
        boolean downPaymentOpen = approved && orderOpen && payment != null
            && (payment.getPaymentStatus() == PaymentStatus.PENDING || payment.getPaymentStatus() == PaymentStatus.FAILED);
        String vehicles = order.getItems().stream()
            .sorted(Comparator.comparing(OrderItem::getId))
            .map(item -> item.getProductName() + " × " + item.getQuantity())
            .collect(Collectors.joining(", "));
        User reviewer = a.getReviewedBy();
        String reviewerName = !forStaff || reviewer == null ? null
            : ((reviewer.getFirstName() == null ? "" : reviewer.getFirstName()) + " " + (reviewer.getLastName() == null ? "" : reviewer.getLastName())).trim();
        return new InstallmentApplicationResponse(
            a.getId(), order.getId(), order.getOrderNumber(), order.getStatus().name(), name, vehicles,
            a.getIdNumberMasked(), a.getMonthlyIncome(), a.getOrderTotal(), a.getDownPaymentPercent(), a.getDownPaymentAmount(),
            a.getFinancedAmount(), a.getTermMonths(), a.getAnnualRatePercent(), a.getMonthlyPayment(), a.getTotalPayable(),
            a.getStatus(), a.getReviewNote(), reviewerName, a.getReviewedAt(), a.getCreatedAt(),
            payment == null ? null : payment.getPaymentStatus().name(),
            downPaymentOpen && a.getReviewedAt() != null ? a.getReviewedAt().plus(downPaymentHold) : null,
            downPaymentOpen,
            InstallmentApplication.PENDING.equals(a.getStatus()) && orderOpen);
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return users.findByUsername(authentication.getName()).or(() -> users.findByEmail(authentication.getName())).orElse(null);
    }

    private static boolean isBackOffice(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> PermissionConstants.OrderManagement.ORDER_VIEW_ALL.equals(authority.getAuthority()));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, RequestLanguage.text("Không tìm thấy hồ sơ trả góp.", "The instalment application was not found."));
    }

    private static ResponseStatusException bad(String vietnamese, String english) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, RequestLanguage.text(vietnamese, english));
    }
}
