package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.dto.installment.InstallmentApplicationResponse;
import com.ebike.dto.installment.InstallmentDecisionRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.event.notification.InstallmentNoticeEvent;
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
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.installment.InstallmentMessages;
import com.ebike.service.installment.InstallmentService;
import com.ebike.service.order.OrderService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstallmentServiceTest {

    @Mock InstallmentApplicationRepository applications;
    @Mock UserRepository users;
    @Mock OrderService orders;
    @Mock OrderRepository orderRepository;
    @Mock AuditLogService audit;

    Fx.Events events;
    InstallmentService service;
    User customer;
    User staff;
    Order order;
    InstallmentApplication application;

    Authentication manager;       // can see every order and decide
    Authentication viewer;        // can see every order, cannot decide
    Authentication customerAuth;
    Authentication stranger;

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static Authentication auth(String name, String... authorities) {
        return new UsernamePasswordAuthenticationToken(name, "n/a", java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @BeforeEach
    void setUp() {
        events = new Fx.Events();
        service = new InstallmentService(applications, users, orders, orderRepository, audit, Duration.ofHours(72), Duration.ofHours(72), "Asia/Ho_Chi_Minh");
        customer = Fx.user(7L, "an");
        staff = Fx.user(1L, "boss");
        staff.setFirstName("Binh");
        staff.setLastName("Tran");

        order = new Order();
        order.setId(100L);
        order.setOrderNumber("ORD-100");
        order.setStatus(OrderStatus.PENDING);
        OrderItem second = new OrderItem();
        second.setId(2L);
        second.setProductName("Battery B");
        second.setQuantity(2);
        OrderItem first = new OrderItem();
        first.setId(1L);
        first.setProductName("Aero X");
        first.setQuantity(1);
        Set<OrderItem> items = new LinkedHashSet<>();
        items.add(second);
        items.add(first);
        order.setItems(items);
        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PENDING);
        order.setPayment(payment);

        application = new InstallmentApplication();
        Fx.set(application, "id", 50L);
        application.setOrder(order);
        application.setUser(customer);
        application.setIdNumberMasked("********6789");
        application.setMonthlyIncome(d("10000000"));
        application.setOrderTotal(d("20000000"));
        application.setDownPaymentPercent(20);
        application.setDownPaymentAmount(d("4000000"));
        application.setFinancedAmount(d("16000000"));
        application.setTermMonths(6);
        application.setAnnualRatePercent(d("12"));
        application.setMonthlyPayment(d("2826667"));
        application.setTotalPayable(d("20960002"));
        Fx.set(application, "createdAt", LocalDateTime.now().minusHours(1));
        Fx.set(application, "updatedAt", LocalDateTime.now().minusHours(1));

        manager = auth("boss", PermissionConstants.OrderManagement.ORDER_VIEW_ALL, PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS);
        viewer = auth("viewer", PermissionConstants.OrderManagement.ORDER_VIEW_ALL);
        customerAuth = auth("an");
        stranger = auth("mallory");

        when(users.findByUsername("an")).thenReturn(Optional.of(customer));
        when(users.findByUsername("boss")).thenReturn(Optional.of(staff));
        when(users.findByUsername("viewer")).thenReturn(Optional.of(Fx.user(3L, "viewer")));
        when(users.findByUsername("mallory")).thenReturn(Optional.of(Fx.user(9L, "mallory")));
        when(applications.findByOrderId(100L)).thenReturn(Optional.of(application));
        when(applications.findOrderIdById(50L)).thenReturn(Optional.of(100L));
        when(applications.findLockedById(50L)).thenReturn(Optional.of(application));
        when(orderRepository.findLockedById(100L)).thenReturn(Optional.of(order));
        when(applications.saveAndFlush(any(InstallmentApplication.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        events.close();
    }

    ResponseStatusException refused(Runnable call) {
        Throwable t = catchThrowable(call::run);
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        return (ResponseStatusException) t;
    }

    static InstallmentDecisionRequest decision(String decision, String note) {
        return new InstallmentDecisionRequest(decision, note);
    }

    // ------------------------------------------------------------------ forOrder (ownership)

    @Test
    void theOwnerSeesTheApplicationOfTheirOrderWithTheirFinanceFigures() {
        InstallmentApplicationResponse r = service.forOrder(100L, customerAuth);
        assertThat(r).isNotNull();
        assertThat(r.id()).isEqualTo(50L);
        assertThat(r.idNumberMasked()).isEqualTo("********6789");
        assertThat(r.monthlyIncome()).isEqualByComparingTo("10000000");
        assertThat(r.reviewedBy()).isNull(); // reviewer names are for the staff
        assertThat(r.vehicles()).isEqualTo("Aero X × 1, Battery B × 2"); // sorted by item id
    }

    @Test
    void anotherCustomerGetsNothingNotAnError() {
        assertThat(service.forOrder(100L, stranger)).isNull();
    }

    @Test
    void anAnonymousCallerOrUnknownAccountGetsNothing() {
        assertThat(service.forOrder(100L, null)).isNull();
        assertThat(service.forOrder(100L, auth(""))).isNull();
        assertThat(service.forOrder(100L, auth("ghost"))).isNull();
    }

    @Test
    void anOrderWithoutAnApplicationIsNull() {
        when(applications.findByOrderId(404L)).thenReturn(Optional.empty());
        assertThat(service.forOrder(404L, customerAuth)).isNull();
    }

    @Test
    void theAccountIsAlsoFoundByEmailWhenTheNameIsAnEmail() {
        when(users.findByUsername("an@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("an@example.com")).thenReturn(Optional.of(customer));
        assertThat(service.forOrder(100L, auth("an@example.com"))).isNotNull();
    }

    @Test
    void staffWithViewAllSeeAnyApplicationButOnlyDecidersSeeIncomeAndIdNumber() {
        InstallmentApplicationResponse asViewer = service.forOrder(100L, viewer);
        assertThat(asViewer).isNotNull();
        assertThat(asViewer.idNumberMasked()).isNull();
        assertThat(asViewer.monthlyIncome()).isNull();
        assertThat(asViewer.canDecide()).isTrue(); // a state flag (pending, order open, hold not over); the pages gate the buttons by permission
        assertThat(asViewer.orderTotal()).isEqualByComparingTo("20000000");

        InstallmentApplicationResponse asManager = service.forOrder(100L, manager);
        assertThat(asManager.idNumberMasked()).isEqualTo("********6789");
        assertThat(asManager.monthlyIncome()).isEqualByComparingTo("10000000");
        assertThat(asManager.canDecide()).isTrue();
    }

    // ------------------------------------------------------------------ the response flags

    @Test
    void aPendingApplicationShowsWhenTheReviewHoldEndsAndCanBeDecided() {
        InstallmentApplicationResponse r = service.forOrder(100L, manager);
        assertThat(r.status()).isEqualTo("PENDING");
        assertThat(r.reviewBefore()).isNotNull();
        assertThat(r.payBefore()).isNull();
        assertThat(r.canPay()).isFalse();
        assertThat(r.canDecide()).isTrue();
        assertThat(r.reviewBefore().getOffset().getTotalSeconds()).isEqualTo(7 * 3600);
        OffsetDateTime expected = application.getCreatedAt().plusHours(72).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        assertThat(r.reviewBefore().toInstant()).isEqualTo(expected.toInstant());
    }

    @Test
    void pastTheReviewHoldNobodyCanDecideAnyMore() {
        Fx.set(application, "createdAt", LocalDateTime.now().minusHours(73));
        assertThat(service.forOrder(100L, manager).canDecide()).isFalse();
    }

    @Test
    void anApprovedApplicationWithAnUnpaidDownPaymentCanBePaidUntilItsHoldEnds() {
        application.setStatus(InstallmentApplication.APPROVED);
        application.setReviewedAt(LocalDateTime.now().minusHours(10));
        InstallmentApplicationResponse r = service.forOrder(100L, customerAuth);
        assertThat(r.canPay()).isTrue();
        assertThat(r.payBefore()).isNotNull();
        assertThat(r.canDecide()).isFalse();
        assertThat(r.reviewBefore()).isNull();
        assertThat(r.paymentStatus()).isEqualTo("PENDING");
    }

    @Test
    void aFailedDownPaymentCanBeRetried() {
        application.setStatus(InstallmentApplication.APPROVED);
        application.setReviewedAt(LocalDateTime.now().minusHours(10));
        order.getPayment().setPaymentStatus(PaymentStatus.FAILED);
        assertThat(service.forOrder(100L, customerAuth).canPay()).isTrue();
    }

    @Test
    void pastTheDownPaymentHoldNobodyMayPayAnyMore() {
        application.setStatus(InstallmentApplication.APPROVED);
        application.setReviewedAt(LocalDateTime.now().minusHours(73));
        assertThat(service.forOrder(100L, customerAuth).canPay()).isFalse();
    }

    @Test
    void aPaidDownPaymentCannotBePaidAgain() {
        application.setStatus(InstallmentApplication.APPROVED);
        application.setReviewedAt(LocalDateTime.now().minusHours(1));
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        InstallmentApplicationResponse r = service.forOrder(100L, customerAuth);
        assertThat(r.canPay()).isFalse();
        assertThat(r.payBefore()).isNull();
    }

    @Test
    void aCancelledOrderOffersNeitherPayingNorDeciding() {
        order.setStatus(OrderStatus.CANCELLED);
        InstallmentApplicationResponse pending = service.forOrder(100L, manager);
        assertThat(pending.canDecide()).isFalse();
        assertThat(pending.reviewBefore()).isNull();
        application.setStatus(InstallmentApplication.APPROVED);
        application.setReviewedAt(LocalDateTime.now());
        assertThat(service.forOrder(100L, customerAuth).canPay()).isFalse();
    }

    @Test
    void aRejectedApplicationNeverOffersPaying() {
        application.setStatus(InstallmentApplication.REJECTED);
        application.setReviewedAt(LocalDateTime.now());
        assertThat(service.forOrder(100L, customerAuth).canPay()).isFalse();
    }

    @Test
    void theReviewerNameIsShownToTheStaffOnly() {
        application.setReviewedBy(staff);
        assertThat(service.forOrder(100L, manager).reviewedBy()).isEqualTo("Binh Tran");
        assertThat(service.forOrder(100L, customerAuth).reviewedBy()).isNull();
    }

    @Test
    void anErasedCustomerShowsAPlaceholderName() {
        customer.setAccountStatus("DELETED");
        assertThat(service.forOrder(100L, manager).customerName()).isEqualTo("Tài khoản đã xóa");
    }

    @Test
    void anOrderWithoutAPaymentRowHasNoPaymentStatusAndCannotBePaid() {
        order.setPayment(null);
        application.setStatus(InstallmentApplication.APPROVED);
        application.setReviewedAt(LocalDateTime.now());
        InstallmentApplicationResponse r = service.forOrder(100L, customerAuth);
        assertThat(r.paymentStatus()).isNull();
        assertThat(r.canPay()).isFalse();
    }

    @Test
    void erasedPersonalDataStaysNullInTheResponse() {
        application.setIdNumberMasked(null);
        application.setMonthlyIncome(null);
        InstallmentApplicationResponse r = service.forOrder(100L, manager);
        assertThat(r.idNumberMasked()).isNull();
        assertThat(r.monthlyIncome()).isNull();
    }

    // ------------------------------------------------------------------ mine

    @Test
    void mineListsOnlyTheOpenApplicationsOfACustomer() {
        when(applications.findOpenOf(7L)).thenReturn(List.of(application));
        assertThat(service.mine(customerAuth)).hasSize(1);
        verify(applications).findOpenOf(7L);
    }

    @Test
    void mineIsEmptyForStaffAndForAnonymous() {
        assertThat(service.mine(manager)).isEmpty();
        assertThat(service.mine(null)).isEmpty();
        assertThat(service.mine(auth("ghost"))).isEmpty();
        verify(applications, never()).findOpenOf(anyLong());
    }

    // ------------------------------------------------------------------ queue

    private ArgumentCaptor<Pageable> page() {
        return ArgumentCaptor.forClass(Pageable.class);
    }

    @Test
    void theDefaultQueueIsThePendingOnesOldestFirstUpTo200() {
        when(applications.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(Pageable.class))).thenReturn(List.of(application));
        assertThat(service.queue(null, manager)).hasSize(1);
        ArgumentCaptor<Pageable> p = page();
        verify(applications).findByStatusOrderByCreatedAtAsc(eq("PENDING"), p.capture());
        assertThat(p.getValue().getPageSize()).isEqualTo(200).isEqualTo(InstallmentService.QUEUE_LIMIT);
        assertThat(p.getValue().getSort()).isEqualTo(Sort.unsorted());
    }

    @Test
    void aClosedStatusListsNewestFirstAndAnyCaseOrSpacingIsAccepted() {
        when(applications.findByStatusOrderByCreatedAtDesc(eq("REJECTED"), any(Pageable.class))).thenReturn(List.of());
        service.queue("  rejected ", manager);
        verify(applications).findByStatusOrderByCreatedAtDesc(eq("REJECTED"), any(Pageable.class));
        service.queue("approved", manager);
        service.queue("CANCELLED", manager);
        verify(applications).findByStatusOrderByCreatedAtDesc(eq("APPROVED"), any(Pageable.class));
        verify(applications).findByStatusOrderByCreatedAtDesc(eq("CANCELLED"), any(Pageable.class));
    }

    @Test
    void allListsEveryStatusNewestFirst() {
        service.queue("all", manager);
        verify(applications).findAllByOrderByCreatedAtDesc(any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOGUS", "DONE", "PENDING,APPROVED", "1"})
    void anUnknownQueueStatusIsRefused(String status) {
        assertThat(refused(() -> service.queue(status, manager)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void blankStatusMeansPending() {
        service.queue("   ", manager);
        verify(applications).findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(Pageable.class));
    }

    @Test
    void theQueueLimitIsClampedBetweenOneAndOneThousand() {
        service.queue("PENDING", manager, 99999);
        service.queue("PENDING", manager, 0);
        service.queue("PENDING", manager, -5);
        service.queue("PENDING", manager, 25);
        ArgumentCaptor<Pageable> p = page();
        verify(applications, org.mockito.Mockito.times(4)).findByStatusOrderByCreatedAtAsc(eq("PENDING"), p.capture());
        assertThat(p.getAllValues()).extracting(Pageable::getPageSize).containsExactly(1000, 1, 1, 25);
    }

    @Test
    void theQueueHidesIncomeAndIdNumberFromStaffWhoCannotDecide() {
        when(applications.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(Pageable.class))).thenReturn(List.of(application));
        InstallmentApplicationResponse forViewer = service.queue(null, viewer).get(0);
        assertThat(forViewer.monthlyIncome()).isNull();
        assertThat(forViewer.idNumberMasked()).isNull();
        InstallmentApplicationResponse forManager = service.queue(null, manager).get(0);
        assertThat(forManager.monthlyIncome()).isEqualByComparingTo("10000000");
    }

    @Test
    void backlogCountsThePendingOnes() {
        when(applications.countByStatus("PENDING")).thenReturn(5L);
        assertThat(service.backlog().pending()).isEqualTo(5L);
    }

    @Test
    void noteLengthsAreTheLimitMinusThePrefixForARejection() {
        assertThat(InstallmentService.maxNoteLength(true)).isEqualTo(1000);
        assertThat(InstallmentService.maxNoteLength(false)).isEqualTo(1000 - "Hồ sơ trả góp bị từ chối: ".length());
    }

    // ------------------------------------------------------------------ decide: validation

    @Test
    void aDecisionOtherThanApproveOrRejectIsRefused() {
        for (String bad : new String[] {null, "", "  ", "MAYBE", "APPROVED", "approve now"}) {
            assertThat(refused(() -> service.decide(50L, decision(bad, "x"), manager)).getReason()).contains("APPROVE hoặc REJECT");
        }
        assertThat(refused(() -> service.decide(50L, null, manager)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void aRejectionNeedsAReason() {
        assertThat(refused(() -> service.decide(50L, decision("REJECT", null), manager)).getReason()).contains("lý do từ chối");
        assertThat(refused(() -> service.decide(50L, decision("REJECT", "   "), manager)).getReason()).contains("lý do từ chối");
        verify(orders, never()).updateOrderStatus(anyLong(), any(), any());
    }

    @Test
    void anApprovalMayHaveNoNote() {
        service.decide(50L, decision("APPROVE", null), manager);
        assertThat(application.getReviewNote()).isNull();
        assertThat(application.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void noteLengthLimitsDifferForApprovalAndRejection() {
        service.decide(50L, decision("APPROVE", "x".repeat(1000)), manager);
        application.setStatus(InstallmentApplication.PENDING);
        assertThat(refused(() -> service.decide(50L, decision("APPROVE", "x".repeat(1001)), manager)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        int max = InstallmentService.maxNoteLength(false);
        assertThat(refused(() -> service.decide(50L, decision("REJECT", "x".repeat(max + 1)), manager)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        service.decide(50L, decision("REJECT", "x".repeat(max)), manager);
        assertThat(application.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void theNoteLengthIsMeasuredAfterTrimmingAndTheDecisionIsCaseInsensitive() {
        service.decide(50L, decision("  approve ", "  " + "x".repeat(1000) + "  "), manager);
        assertThat(application.getReviewNote()).hasSize(1000);
    }

    @Test
    void unknownApplicationOrOrderIs404() {
        when(applications.findOrderIdById(404L)).thenReturn(Optional.empty());
        assertThat(refused(() -> service.decide(404L, decision("APPROVE", null), manager)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        when(orderRepository.findLockedById(100L)).thenReturn(Optional.empty());
        assertThat(refused(() -> service.decide(50L, decision("APPROVE", null), manager)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theOrderRowIsLockedBeforeTheApplicationRow() {
        service.decide(50L, decision("APPROVE", null), manager);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(applications, orderRepository);
        order.verify(applications).findOrderIdById(50L);
        order.verify(orderRepository).findLockedById(100L);
        order.verify(applications).findLockedById(50L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "CANCELLED"})
    void anAlreadyDecidedApplicationCannotBeDecidedAgain(String status) {
        application.setStatus(status);
        ResponseStatusException e = refused(() -> service.decide(50L, decision("APPROVE", "again"), manager));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(applications, never()).saveAndFlush(any());
        assertThat(events.published).isEmpty();
    }

    @Test
    void approvingTwiceOnlyWorksOnce() {
        service.decide(50L, decision("APPROVE", null), manager);
        assertThat(refused(() -> service.decide(50L, decision("APPROVE", null), manager)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(events.published).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "READY_FOR_PICKUP"})
    void anOrderThatLeftPendingCannotHaveItsApplicationDecided(String status) {
        order.setStatus(OrderStatus.valueOf(status));
        ResponseStatusException e = refused(() -> service.decide(50L, decision("REJECT", "late"), manager));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(application.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void approvingAfterTheReviewHoldIsRefusedButRejectingStillWorks() {
        Fx.set(application, "createdAt", LocalDateTime.now().minusHours(73));
        ResponseStatusException e = refused(() -> service.decide(50L, decision("APPROVE", null), manager));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getReason()).contains("quá thời hạn duyệt");
        service.decide(50L, decision("REJECT", "too late anyway"), manager);
        assertThat(application.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void approvingJustInsideTheReviewHoldWorks() {
        Fx.set(application, "createdAt", LocalDateTime.now().minusHours(71).minusMinutes(59));
        service.decide(50L, decision("APPROVE", null), manager);
        assertThat(application.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void theReviewHoldFollowsTheSetting() {
        InstallmentService shortHold = new InstallmentService(applications, users, orders, orderRepository, audit, Duration.ofHours(72), Duration.ofHours(1), "Asia/Ho_Chi_Minh");
        Fx.set(application, "createdAt", LocalDateTime.now().minusHours(2));
        assertThat(refused(() -> shortHold.decide(50L, decision("APPROVE", null), manager)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anErasedAccountCanBeRejectedButNotApproved() {
        customer.setAccountStatus("DELETED");
        assertThat(refused(() -> service.decide(50L, decision("APPROVE", null), manager)).getReason()).contains("đã bị xóa");
        service.decide(50L, decision("REJECT", "account erased"), manager);
        assertThat(application.getStatus()).isEqualTo("REJECTED");
    }

    // ------------------------------------------------------------------ decide: approve

    @Test
    void approvalRecordsWhoWhenAndWhy() {
        LocalDateTime before = LocalDateTime.now();
        InstallmentApplicationResponse r = service.decide(50L, decision("approve", "  Bring your ID  "), manager);
        LocalDateTime after = LocalDateTime.now();

        assertThat(application.getStatus()).isEqualTo("APPROVED");
        assertThat(application.getReviewNote()).isEqualTo("Bring your ID");
        assertThat(application.getReviewedBy()).isSameAs(staff);
        assertThat(application.getReviewedAt()).isBetween(before, after);
        verify(applications).saveAndFlush(application);
        verify(audit).record("duyệt hồ sơ trả góp", "ORD-100");
        assertThat(r.status()).isEqualTo("APPROVED");
        assertThat(r.canPay()).isTrue();
        assertThat(r.reviewedBy()).isEqualTo("Binh Tran");
        verify(orders, never()).updateOrderStatus(anyLong(), any(), any()); // approving keeps the order PENDING until the down payment arrives
    }

    @Test
    void approvalGivesTheCustomerSeventyTwoHoursFromNowToPayTheDownPaymentInTheShopOffset() {
        LocalDateTime before = LocalDateTime.now();
        service.decide(50L, decision("APPROVE", null), manager);
        LocalDateTime after = LocalDateTime.now();

        assertThat(events.published).singleElement().isInstanceOfSatisfying(InstallmentNoticeEvent.class, e -> {
            assertThat(e.kind()).isEqualTo(InstallmentMessages.APPROVED);
            assertThat(e.userId()).isEqualTo(7L);
            assertThat(e.orderId()).isEqualTo(100L);
            assertThat(e.orderNumber()).isEqualTo("ORD-100");
            assertThat(e.downPayment()).isEqualByComparingTo("4000000");
            assertThat(e.monthlyPayment()).isEqualByComparingTo("2826667");
            assertThat(e.termMonths()).isEqualTo(6);
            assertThat(e.payBefore().getOffset().getTotalSeconds()).isEqualTo(7 * 3600);
            assertThat(e.payBefore().toInstant()).isBetween(
                before.plusHours(72).atZone(ZoneId.systemDefault()).toInstant(),
                after.plusHours(72).atZone(ZoneId.systemDefault()).toInstant());
        });
    }

    @Test
    void theDownPaymentHoldFollowsTheSetting() {
        InstallmentService twelve = new InstallmentService(applications, users, orders, orderRepository, audit, Duration.ofHours(12), Duration.ofHours(72), "Asia/Ho_Chi_Minh");
        LocalDateTime before = LocalDateTime.now();
        twelve.decide(50L, decision("APPROVE", null), manager);
        assertThat(events.published).singleElement().isInstanceOfSatisfying(InstallmentNoticeEvent.class, e ->
            assertThat(e.payBefore().toInstant()).isBetween(before.plusHours(12).atZone(ZoneId.systemDefault()).toInstant(),
                LocalDateTime.now().plusHours(12).atZone(ZoneId.systemDefault()).toInstant()));
    }

    @Test
    void theOffsetOfTheDeadlineFollowsTheConfiguredZone() {
        InstallmentService tokyo = new InstallmentService(applications, users, orders, orderRepository, audit, Duration.ofHours(72), Duration.ofHours(72), "Asia/Tokyo");
        tokyo.decide(50L, decision("APPROVE", null), manager);
        assertThat(events.published).singleElement().isInstanceOfSatisfying(InstallmentNoticeEvent.class, e ->
            assertThat(e.payBefore().getOffset().getTotalSeconds()).isEqualTo(9 * 3600));
    }

    @Test
    void approvalPutsTheStaffNoteIntoTheNotice() {
        service.decide(50L, decision("APPROVE", "bring cash"), manager);
        assertThat(events.published).singleElement().isInstanceOfSatisfying(InstallmentNoticeEvent.class, e -> assertThat(e.note()).isEqualTo("bring cash"));
    }

    // ------------------------------------------------------------------ decide: reject

    @Test
    void rejectionCancelsTheOrderWithThePrefixedReasonAndSilencesTheGenericNotice() {
        InstallmentApplicationResponse r = service.decide(50L, decision("REJECT", "  Income too low "), manager);

        assertThat(application.getStatus()).isEqualTo("REJECTED");
        assertThat(application.getReviewNote()).isEqualTo("Income too low");
        assertThat(order.isSuppressStatusNotice()).isTrue();
        ArgumentCaptor<OrderStatusUpdateRequest> request = ArgumentCaptor.forClass(OrderStatusUpdateRequest.class);
        verify(orders).updateOrderStatus(eq(100L), request.capture(), eq(manager));
        assertThat(request.getValue().status()).isEqualTo("CANCELLED");
        assertThat(request.getValue().reason()).isEqualTo(InstallmentService.REJECTED_REASON_PREFIX + "Income too low");
        verify(audit).record("từ chối hồ sơ trả góp", "ORD-100");
        assertThat(r.status()).isEqualTo("REJECTED");
        assertThat(r.canPay()).isFalse();
        assertThat(events.published).singleElement().isInstanceOfSatisfying(InstallmentNoticeEvent.class, e -> {
            assertThat(e.kind()).isEqualTo(InstallmentMessages.REJECTED);
            assertThat(e.payBefore()).isNull();
            assertThat(e.note()).isEqualTo("Income too low");
        });
    }

    @Test
    void theWholeCancellationReasonOfARejectionStaysWithinTheOrdersThousandCharacters() {
        service.decide(50L, decision("REJECT", "x".repeat(InstallmentService.maxNoteLength(false))), manager);
        ArgumentCaptor<OrderStatusUpdateRequest> request = ArgumentCaptor.forClass(OrderStatusUpdateRequest.class);
        verify(orders).updateOrderStatus(eq(100L), request.capture(), any());
        assertThat(request.getValue().reason()).hasSize(1000);
    }

    @Test
    void aRejectionPersistsTheVerdictBeforeCancellingTheOrder() {
        service.decide(50L, decision("REJECT", "no"), manager);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(applications, orders);
        order.verify(applications).saveAndFlush(application);
        order.verify(orders).updateOrderStatus(anyLong(), any(), any());
    }
}
