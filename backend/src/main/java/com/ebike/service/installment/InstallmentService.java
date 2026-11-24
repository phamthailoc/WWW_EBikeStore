package com.ebike.service.installment;

import com.ebike.common.constants.PermissionConstants;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
import org.springframework.transaction.annotation.Isolation;
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
    /** The most rows one queue request may ask for. */
    public static final int QUEUE_MAX = 1000;
    private static final int MAX_NOTE_LENGTH = 1000;
    /** What the cancellation reason of a rejected application starts with; the order page words it in the language of the viewer. */
    public static final String REJECTED_REASON_PREFIX = "Hồ sơ trả góp bị từ chối: ";
    private static final Set<String> QUEUE_STATUSES = Set.of(
        InstallmentApplication.PENDING, InstallmentApplication.APPROVED, InstallmentApplication.REJECTED, InstallmentApplication.CANCELLED);

    private final InstallmentApplicationRepository applications;
    private final UserRepository users;
    private final OrderService orders;
    private final OrderRepository orderRepository;
    private final AuditLogService auditLog;
    // how long an approved application waits for its down payment before the order is cancelled (the expiry job reads the same setting)
    private final Duration downPaymentHold;
    // how long a submitted application waits for the staff before the order is cancelled (same setting as the expiry job)
    private final Duration reviewHold;
    // the shop's zone (app.time-zone): the times of an application are sent and worded with their offset in it, whatever zone the server runs in
    private final ZoneId zone;

    public InstallmentService(
        InstallmentApplicationRepository applications,
        UserRepository users,
        OrderService orders,
        OrderRepository orderRepository,
        AuditLogService auditLog,
        @Value("${app.order.installment-down-payment-hold:PT72H}") Duration downPaymentHold,
        @Value("${app.order.installment-review-hold:PT72H}") Duration reviewHold,
        @Value("${app.time-zone:Asia/Ho_Chi_Minh}") String timeZone
    ) {
        this.applications = applications;
        this.users = users;
        this.orders = orders;
        this.orderRepository = orderRepository;
        this.auditLog = auditLog;
        this.downPaymentHold = downPaymentHold;
        this.reviewHold = reviewHold;
        this.zone = ZoneId.of(timeZone);
    }

    /** A time the server stamped with its own clock (a local date-time), as a moment with the shop's offset; null stays null. */
    private OffsetDateTime inShopZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone).toOffsetDateTime();
    }

    /**
     * The application of one order, for its customer or for the staff; null (the controller answers 204) when the order has none or is somebody else's, so
     * every order page can ask without an error and an order number cannot be probed.
     */
    @Transactional(readOnly = true)
    public InstallmentApplicationResponse forOrder(Long orderId, Authentication authentication) {
        InstallmentApplication application = applications.findByOrderId(orderId).orElse(null);
        if (application == null) {
            return null;
        }
        boolean staff = isBackOffice(authentication);
        if (!staff) {
            User current = currentUser(authentication);
            if (current == null || !current.getId().equals(application.getUser().getId())) {
                return null;
            }
        }
        return toResponse(application, staff, !staff || canDecide(authentication));
    }

    /** The customer's own applications that still wait for the staff or for the down payment, so the list of orders can say which ones need attention. */
    @Transactional(readOnly = true)
    public List<InstallmentApplicationResponse> mine(Authentication authentication) {
        User current = isBackOffice(authentication) ? null : currentUser(authentication);
        if (current == null) {
            return List.of();
        }
        return applications.findOpenOf(current.getId()).stream().map(row -> toResponse(row, false, true)).toList();
    }

    /** The queue: {@code status} is PENDING (the default), ALL, or one of the other statuses. Oldest waiting first; a closed list shows the newest first. */
    @Transactional(readOnly = true)
    public List<InstallmentApplicationResponse> queue(String status, Authentication authentication) {
        return queue(status, authentication, null);
    }

    /** {@code limit} rows at most (default {@link #QUEUE_LIMIT}, never more than {@link #QUEUE_MAX}); the page asks for more with a "load more" button. */
    @Transactional(readOnly = true)
    public List<InstallmentApplicationResponse> queue(String status, Authentication authentication, Integer limit) {
        int size = limit == null ? QUEUE_LIMIT : Math.max(1, Math.min(limit, QUEUE_MAX));
        String wanted = status == null || status.isBlank() ? InstallmentApplication.PENDING : status.trim().toUpperCase(Locale.ROOT);
        List<InstallmentApplication> rows;
        if ("ALL".equals(wanted)) {
            rows = applications.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size));
        } else if (QUEUE_STATUSES.contains(wanted)) {
            rows = InstallmentApplication.PENDING.equals(wanted)
                ? applications.findByStatusOrderByCreatedAtAsc(wanted, PageRequest.of(0, size))
                : applications.findByStatusOrderByCreatedAtDesc(wanted, PageRequest.of(0, size));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
        }
        boolean seesFinance = canDecide(authentication);
        return rows.stream().map(row -> toResponse(row, true, seesFinance)).toList();
    }

    /**
     * How long the staff's note can be: an approval note as a whole, a rejection note less the prefix it gets in the order's cancellation reason (which has
     * the same limit as a whole). The staff page asks for it through the config, so the form and the check here cannot drift apart.
     */
    public static int maxNoteLength(boolean approve) {
        return approve ? MAX_NOTE_LENGTH : MAX_NOTE_LENGTH - REJECTED_REASON_PREFIX.length();
    }

    @Transactional(readOnly = true)
    public InstallmentBacklogResponse backlog() {
        return new InstallmentBacklogResponse(applications.countByStatus(InstallmentApplication.PENDING));
    }

    /**
     * The staff's verdict. A rejection cancels the order (with the reason), an approval opens the down payment.
     * READ_COMMITTED: the order is found through a bare id before its row can be locked, and under the default snapshot everything read after waiting for
     * that lock (the order's items, the stock of the vehicles a rejection puts back) would be as old as that first read, so a rejection could write back
     * a stock that another order changed while it waited. With READ_COMMITTED each read sees what is committed when it runs.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InstallmentApplicationResponse decide(Long id, InstallmentDecisionRequest request, Authentication authentication) {
        String decision = request == null || request.decision() == null ? "" : request.decision().trim().toUpperCase(Locale.ROOT);
        boolean approve = "APPROVE".equals(decision);
        if (!approve && !"REJECT".equals(decision)) {
            throw bad("Quyết định phải là APPROVE hoặc REJECT.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        // a rejection note becomes the tail of the order's cancellation reason, which has the same limit as a whole
        int maxNote = maxNoteLength(approve);
        if (note != null && note.length() > maxNote) {
            throw bad("Ghi chú tối đa " + maxNote + " ký tự.");
        }
        if (!approve && note == null) {
            throw bad("Vui lòng nhập lý do từ chối (khách sẽ thấy).");
        }
        // The locks, always in this order (every cancellation of an order takes them the same way): the order row first, then the application row. So an
        // approval and a rejection pressed at once, or a cancellation of the order, wait for each other and then see what the winner did.
        Long orderId = applications.findOrderIdById(id).orElseThrow(InstallmentService::notFound);
        Order order = orderRepository.findLockedById(orderId).orElseThrow(InstallmentService::notFound);
        InstallmentApplication application = applications.findLockedById(id).orElseThrow(InstallmentService::notFound);
        if (!InstallmentApplication.PENDING.equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ này đã được xử lý hoặc đơn đã bị hủy. Hãy tải lại danh sách.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không còn ở trạng thái chờ xác nhận nên không duyệt được hồ sơ.");
        }
        // past the review hold the expiry job is about to cancel the order: approving now would only restart the clock of an order the store let lapse
        if (approve && reviewOverdue(application, LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã quá thời hạn duyệt hồ sơ nên đơn hàng sắp bị hủy tự động. Không duyệt thêm được.");
        }
        // an application of an account erased before this was checked can still be rejected (that frees the order's stock), but not approved
        if (approve && AccountErasure.DELETED.equals(application.getUser().getAccountStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản của khách đã bị xóa, không duyệt được hồ sơ.");
        }

        LocalDateTime now = LocalDateTime.now();
        application.setReviewNote(note);
        application.setReviewedBy(currentUser(authentication));
        application.setReviewedAt(now);
        application.setStatus(approve ? InstallmentApplication.APPROVED : InstallmentApplication.REJECTED);
        applications.saveAndFlush(application);
        if (!approve) {
            // the order goes back with its stock; the closing of the open application skips a rejected one, so the verdict stays.
            // The customer is told by the notice and the email of the rejection below, not by the generic "order cancelled" notice too.
            order.setSuppressStatusNotice(true);
            orders.updateOrderStatus(order.getId(), new OrderStatusUpdateRequest("CANCELLED", REJECTED_REASON_PREFIX + note), authentication);
        }
        auditLog.record(approve ? "duyệt hồ sơ trả góp" : "từ chối hồ sơ trả góp", order.getOrderNumber());
        NotificationEventBridge.publish(new InstallmentNoticeEvent(
            application.getUser().getId(), order.getId(), order.getOrderNumber(),
            approve ? InstallmentMessages.APPROVED : InstallmentMessages.REJECTED,
            application.getDownPaymentAmount(), application.getMonthlyPayment(), application.getTermMonths(),
            approve ? inShopZone(now.plus(downPaymentHold)) : null, note));
        return toResponse(application, true, true);
    }

    private boolean reviewOverdue(InstallmentApplication a, LocalDateTime now) {
        return a.getCreatedAt() != null && now.isAfter(a.getCreatedAt().plus(reviewHold));
    }

    /** {@code seesFinance}: the declared income and the last digits of the ID number go only to a member of the staff who may decide the application, not to every account that may look at the queue. */
    private InstallmentApplicationResponse toResponse(InstallmentApplication a, boolean forStaff, boolean seesFinance) {
        Order order = a.getOrder();
        User user = a.getUser();
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (AccountErasure.DELETED.equals(user.getAccountStatus())) {
            // the erasure wrote its Vietnamese placeholder into the name: the page shows it in the language of the request instead
            name = "Tài khoản đã xóa";
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
            seesFinance ? a.getIdNumberMasked() : null, seesFinance ? a.getMonthlyIncome() : null, a.getOrderTotal(), a.getDownPaymentPercent(), a.getDownPaymentAmount(),
            a.getFinancedAmount(), a.getTermMonths(), a.getAnnualRatePercent(), a.getMonthlyPayment(), a.getTotalPayable(),
            a.getStatus(), a.getReviewNote(), reviewerName, inShopZone(a.getReviewedAt()), inShopZone(a.getCreatedAt()),
            payment == null ? null : payment.getPaymentStatus().name(),
            downPaymentOpen && a.getReviewedAt() != null ? inShopZone(a.getReviewedAt().plus(downPaymentHold)) : null,
            InstallmentApplication.PENDING.equals(a.getStatus()) && orderOpen && a.getCreatedAt() != null ? inShopZone(a.getCreatedAt().plus(reviewHold)) : null,
            // past its hold the order is about to be cancelled by the expiry job: nobody is offered to pay or to decide any more
            downPaymentOpen && !InstallmentRules.downPaymentOverdue(a.getReviewedAt(), downPaymentHold, LocalDateTime.now()),
            InstallmentApplication.PENDING.equals(a.getStatus()) && orderOpen && !reviewOverdue(a, LocalDateTime.now()));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return users.findByUsername(authentication.getName()).or(() -> users.findByEmail(authentication.getName())).orElse(null);
    }

    private static boolean canDecide(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS.equals(authority.getAuthority()));
    }

    private static boolean isBackOffice(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> PermissionConstants.OrderManagement.ORDER_VIEW_ALL.equals(authority.getAuthority()));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ trả góp.");
    }

    private static ResponseStatusException bad(String vietnamese) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, vietnamese);
    }
}
