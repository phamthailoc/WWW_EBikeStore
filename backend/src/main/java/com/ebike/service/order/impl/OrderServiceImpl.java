package com.ebike.service.order.impl;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.event.notification.OrderPlacedEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.dto.order.request.GuestOrderLinkRequest;
import com.ebike.dto.order.request.OrderCancellationRequest;
import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.request.OrderQuoteRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.dto.order.response.OrderItemResponse;
import com.ebike.dto.order.response.OrderItemUnitResponse;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.installment.InstallmentPlanner;
import com.ebike.service.order.OrderUnits;
import com.ebike.service.stock.StockAlertRules;
import com.ebike.service.stock.StockAlertService;
import com.ebike.service.stock.StockLedger;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.dto.order.response.GuestOrderLinkResponse;
import com.ebike.dto.order.response.OrderQuoteResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.dto.order.response.ShipmentResponse;
import com.ebike.dto.order.response.ShowroomResponse;
import com.ebike.model.entity.admin.Promotion;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.service.admin.PricingService;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.ShipmentStatus;
import com.ebike.model.entity.order.Showroom;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.service.order.OrderEmailVerificationService;
import com.ebike.service.order.OrderService;
import com.ebike.service.product.VehicleRegistration;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.repository.product.ProductRepository;
import com.ebike.common.constants.PermissionConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderServiceImpl implements OrderService {

    @Value("${app.order.email-otp.required:false}")
    private boolean emailOtpRequired;

    // How long stock is held for an order that has not been paid or confirmed yet.
    @Value("${app.order.vnpay-expiry:PT30M}")
    private java.time.Duration vnpayExpiry = java.time.Duration.ofMinutes(30);

    @Value("${app.order.pay-later-expiry:PT72H}")
    private java.time.Duration payLaterExpiry = java.time.Duration.ofHours(72);

    // How long an approved instalment application waits for its down payment (InstallmentService reads the same setting).
    @Value("${app.order.installment-down-payment-hold:PT72H}")
    private java.time.Duration installmentDownPaymentHold = java.time.Duration.ofHours(72);

    // How long an application that the staff have not decided yet holds the stock (its own setting: it is not the pay-later hold).
    @Value("${app.order.installment-review-hold:PT72H}")
    private java.time.Duration installmentReviewHold = java.time.Duration.ofHours(72);

    @Value("${app.order.pickup-hold:P7D}")
    private java.time.Duration pickupHold = java.time.Duration.ofDays(7);

    static final String UNITS_REQUIRED = "Hãy nhập đủ số khung và số máy của từng xe trước khi xe rời cửa hàng (giao đi hoặc bàn giao cho khách).";

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MAX_ORDER_ITEMS = 20;
    private static final int MAX_ITEM_QUANTITY = 10;
    private static final int MAX_CUSTOMER_NAME_LENGTH = 100;
    private static final int MAX_DETAILED_ADDRESS_LENGTH = 500;
    private static final int MAX_NOTES_LENGTH = 1000;
    private static final int MAX_CANCELLATION_REASON_LENGTH = 1000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIETNAM_PHONE_PATTERN = Pattern.compile("^0\\d{9,10}$");
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ShowroomRepository showroomRepository;
    private final PromotionRepository promotionRepository;
    private final OrderEmailVerificationService orderEmailVerificationService;
    private final PricingService pricingService;

    private final AuditLogService auditLog;
    private final StockAlertService stockAlerts;
    private final InstallmentPlanner installmentPlanner;
    private final InstallmentApplicationRepository installments;
    private final StockLedger stockLedger;

    public OrderServiceImpl(
        OrderRepository orderRepository,
        UserRepository userRepository,
        ProductRepository productRepository,
        ShowroomRepository showroomRepository,
        PromotionRepository promotionRepository,
        OrderEmailVerificationService orderEmailVerificationService,
        PricingService pricingService,
        AuditLogService auditLog,
        StockAlertService stockAlerts,
        InstallmentPlanner installmentPlanner,
        InstallmentApplicationRepository installments,
        StockLedger stockLedger
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.showroomRepository = showroomRepository;
        this.promotionRepository = promotionRepository;
        this.orderEmailVerificationService = orderEmailVerificationService;
        this.pricingService = pricingService;
        this.auditLog = auditLog;
        this.stockAlerts = stockAlerts;
        this.installmentPlanner = installmentPlanner;
        this.installments = installments;
        this.stockLedger = stockLedger;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(
        Authentication authentication,
        Long userId,
        String status,
        String paymentStatus,
        String search,
        Long showroomId
    ) {
        return getOrders(authentication, userId, status, paymentStatus, search, showroomId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(
        Authentication authentication,
        Long userId,
        String status,
        String paymentStatus,
        String search,
        Long showroomId,
        Integer limit
    ) {
        User currentUser = getAuthenticatedUser(authentication);
        Long effectiveUserId = isBackOffice(authentication) ? userId : currentUser.getId();

        List<Order> orders = effectiveUserId == null
            ? orderRepository.findAll()
            : orderRepository.findByUserId(effectiveUserId);

        return orders.stream()
            .filter(order -> matchesOrderStatus(order, status))
            .filter(order -> matchesPaymentStatus(order, paymentStatus))
            .filter(order -> matchesSearch(order, search))
            .filter(order -> matchesShowroom(order, showroomId))
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .limit(limit == null || limit < 1 ? Long.MAX_VALUE : Math.min(limit, 2000))
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, Authentication authentication) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
        assertCanAccessOrder(order, authentication);
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderQuoteResponse quoteOrder(OrderQuoteRequest request) {
        boolean homeDelivery = request != null && isHomeDelivery(request.deliveryMethod());
        return buildQuote(
            validateQuoteItems(request == null ? null : request.items()),
            shouldIncludeRegistrationService(request == null ? null : request.includeRegistrationService()),
            request == null ? null : request.discountCode(),
            homeDelivery,
            request == null ? null : request.province()
        );
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
        com.ebike.config.CacheNames.PRODUCT_LIST_CACHE,
        com.ebike.config.CacheNames.PRODUCT_DETAIL_CACHE
    }, allEntries = true)
    public OrderResponse createOrder(OrderCreateRequest request, Authentication authentication) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu nội dung yêu cầu.");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng cần có ít nhất một sản phẩm.");
        }

        lockCustomerForInstallment(request, authentication);
        User user = getAuthenticatedUserOrNull(authentication);

        Order order = new Order();
        if (user != null) {
            order.setUser(user);
        }
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.PENDING);
        boolean includeRegistrationService = shouldIncludeRegistrationService(request.includeRegistrationService());
        List<OrderCreateItemRequest> items = validateQuoteItems(request.items());
        assertRegistrationServiceOffered(includeRegistrationService, items, isHomeDelivery(request));
        order.setIncludeRegistrationService(includeRegistrationService);
        validateCheckoutDetails(request);
        if (emailOtpRequired) {
            orderEmailVerificationService.assertVerifiedForOrder(request.emailVerificationSessionId(), request.customerEmail());
        }
        order.setNotes(normalizeOptional(request.notes()));
        order.setCustomerEmail(normalizeEmail(request.customerEmail()));

        Showroom showroom = isHomeDelivery(request) ? null : showroomRepository.findById(request.pickupShowroomId())
            .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cửa hàng nhận xe không còn hoạt động."));

        List<StockLedger.Entry> stockEntries = new ArrayList<>();
        BigDecimal subtotal = addItems(order, items, stockEntries);
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee(isHomeDelivery(request), request.province(), items));
        order.setRegistrationFee(registrationFee(includeRegistrationService, pricingService.registrationServicePerBike(), items));
        Promotion promotion = findValidPromotion(request.discountCode());
        BigDecimal promoDiscountAmount = promotion == null ? ZERO : calculatePromoDiscount(promotion, subtotal);
        if (promotion != null) {
            assertCodeNotUsedBefore(promotion, user, order.getCustomerEmail());
            if (promotionRepository.tryIncrementUsage(promotion.getId()) == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá không hợp lệ hoặc đã hết hạn.");
            }
            order.setPromotion(promotion);
        }
        order.setDiscountAmount(promoDiscountAmount);
        order.setTotalAmount(calculateTotalAmount(subtotal, order.getShippingFee(), order.getDiscountAmount(), order.getRegistrationFee()));
        // V24: an instalment order is checked now (the plan, the ID number, the income) so that a bad application never holds stock
        InstallmentPlanner.Prepared installment = null;
        if (parseCheckoutPaymentMethod(request.paymentMethod()) == PaymentMethod.INSTALLMENT) {
            assertCustomerRole(authentication);
            installment = installmentPlanner.prepare(user, order.getTotalAmount(), request.installment());
        }
        order.setShipment(buildShipment(order, showroom, request));
        attachInitialPayment(order, request.paymentMethod(), installment);

        Order saved = orderRepository.save(order);
        // V26: the sale goes in the stock ledger now that the order has its number and id (nobody in the shop is the actor: the customer or a guest ordered)
        stockLedger.record(stockEntries, saved.getId(), saved.getOrderNumber(), null, null);
        if (installment != null) {
            installmentPlanner.record(saved, user, installment);
        }
        // the confirmation email goes out after the commit, from the mail pool; guest orders get it too
        NotificationEventBridge.publish(new OrderPlacedEvent(saved.getId()));
        return toResponse(saved);
    }

    // The steps a staff member may take from each status. A cancelled or delivered order is final.
    // After PROCESSING the path depends on how the customer gets the bike: home delivery goes through SHIPPED,
    // store pickup waits in READY_FOR_PICKUP until the customer collects it.
    private static List<OrderStatus> nextStatuses(Order order) {
        boolean pickup = order.getShipment() != null && order.getShipment().getPickupShowroom() != null;
        return switch (order.getStatus()) {
            case PENDING -> List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
            case CONFIRMED -> List.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
            case PROCESSING -> List.of(pickup ? OrderStatus.READY_FOR_PICKUP : OrderStatus.SHIPPED, OrderStatus.CANCELLED);
            case READY_FOR_PICKUP -> List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
            case SHIPPED -> List.of(OrderStatus.DELIVERED);
            default -> List.of();
        };
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
        com.ebike.config.CacheNames.PRODUCT_LIST_CACHE,
        com.ebike.config.CacheNames.PRODUCT_DETAIL_CACHE
    }, allEntries = true)
    public OrderResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request, Authentication authentication) {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn trạng thái.");
        }

        Order order = orderRepository.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));

        OrderStatus nextStatus;
        try {
            nextStatus = OrderStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái đơn hàng không hợp lệ.");
        }

        OrderStatus previousStatus = order.getStatus();
        if (nextStatus == OrderStatus.CONFIRMED && order.getPayment() != null && order.getPayment().getPaymentMethod() == PaymentMethod.INSTALLMENT) {
            // V24: the staff confirm an instalment order only once the application is approved and the down payment is in (paying it confirms the order by
            // itself, and confirming earlier would take the order out of PENDING and with it the customer's way to pay)
            boolean approved = installments.findByOrderId(order.getId()).map(a -> InstallmentApplication.APPROVED.equals(a.getStatus())).orElse(false);
            if (!approved) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hồ sơ trả góp chưa được duyệt nên chưa xác nhận được đơn hàng.");
            }
            if (order.getPayment().getPaymentStatus() != PaymentStatus.PAID) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoản trả trước chưa được thanh toán nên chưa xác nhận được đơn hàng.");
            }
        }
        if (!nextStatuses(order).contains(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể chuyển đơn hàng từ " + order.getStatus() + " sang " + nextStatus + ".");
        }

        LocalDateTime cancellableAt = order.getStatus() == OrderStatus.READY_FOR_PICKUP ? order.getPickupDeadlineAt() : null;
        if (nextStatus == OrderStatus.CANCELLED && cancellableAt != null && LocalDateTime.now().isBefore(cancellableAt)) {
            // The customer was told the bike is ready; give them the full hold period before the store gives it up.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn đang chờ khách lấy xe, chỉ được hủy sau "
                + cancellableAt.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")));
        }

        // The numbers are needed before the bike leaves the store: when it ships, or when a pickup customer collects it.
        // An order already SHIPPED has left (one shipped before V18 has no numbers), so delivering it is not blocked,
        // the same as the shipment form in ManagerServiceImpl.
        boolean leavesTheStore = nextStatus == OrderStatus.SHIPPED
            || (nextStatus == OrderStatus.DELIVERED && order.getStatus() != OrderStatus.SHIPPED);
        if (leavesTheStore && !OrderUnits.isComplete(order)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, UNITS_REQUIRED);
        }

        if (nextStatus == OrderStatus.CANCELLED) {
            // Same effects as a customer cancelling: reason saved, pending payment closed, stock returned.
            cancelOrder(order, request.reason(), getAuthenticatedUserOrNull(authentication), "Cancelled by staff");
        } else {
            order.setStatus(nextStatus);
            if (nextStatus == OrderStatus.READY_FOR_PICKUP) {
                order.setPickupDeadlineAt(LocalDateTime.now().plus(pickupHold));
            }
            if (nextStatus == OrderStatus.DELIVERED && order.getShipment() != null) {
                // Pickup orders have no carrier step, so the hand-over time is recorded here.
                Shipment shipment = order.getShipment();
                LocalDateTime now = LocalDateTime.now();
                if (shipment.getShippedAt() == null) {
                    shipment.setShippedAt(now);
                }
                if (shipment.getDeliveredAt() == null) {
                    shipment.setDeliveredAt(now);
                }
                shipment.setShipmentStatus(ShipmentStatus.DELIVERED);
            }
        }

        OrderResponse updated = toResponse(orderRepository.save(order));
        auditLog.record("đổi trạng thái đơn", order.getOrderNumber() + ": " + previousStatus + " → " + nextStatus);
        return updated;
    }

    // READ_COMMITTED: the candidates are read first without a lock; with the default snapshot, what is read after waiting for an order's lock could be older than the lock
    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CacheEvict(cacheNames = {
        com.ebike.config.CacheNames.PRODUCT_LIST_CACHE,
        com.ebike.config.CacheNames.PRODUCT_DETAIL_CACHE
    }, allEntries = true)
    public int expireUnpaidOrders(LocalDateTime now) {
        LocalDateTime vnpayCutoff = now.minus(vnpayExpiry);
        LocalDateTime payLaterCutoff = now.minus(payLaterExpiry);
        LocalDateTime latestCutoff = vnpayCutoff.isAfter(payLaterCutoff) ? vnpayCutoff : payLaterCutoff;
        // an instalment order waits for the staff (from its creation) and then for the down payment (from the approval, which is later): it cannot be due
        // before the shorter of the two holds has passed since it was created
        LocalDateTime instalmentCutoff = now.minus(installmentDownPaymentHold.compareTo(installmentReviewHold) < 0 ? installmentDownPaymentHold : installmentReviewHold);
        if (instalmentCutoff.isAfter(latestCutoff)) {
            latestCutoff = instalmentCutoff;
        }

        // Read without a lock and decide from plain values which orders may be due; only those are locked (in id order), and each is checked again once its
        // row is held, so a customer cancelling or the staff confirming an order that is not due never waits for this job.
        List<Long> possiblyDue = orderRepository.findExpiryCandidates(OrderStatus.PENDING, latestCutoff).stream()
            .filter(candidate -> candidate.getPaymentStatus() != PaymentStatus.PAID)
            .filter(candidate -> candidate.getPaymentMethod() == PaymentMethod.INSTALLMENT
                ? candidate.getCreatedAt().isBefore(instalmentCutoff)
                : candidate.getCreatedAt().isBefore(isOnline(candidate.getPaymentMethod()) ? vnpayCutoff : payLaterCutoff))
            .map(OrderRepository.ExpiryCandidate::getId)
            .sorted()
            .toList();

        // Two passes, so the locks keep the order every other operation takes (an order's row, then its vehicles' rows): first every due order is locked and
        // checked, in id order, touching no vehicle; only then are the vehicles put back. A customer or a staff member cancelling an order holds that
        // order's row and wants its vehicles; the job never holds a vehicle while it still waits for an order, so the two cannot wait for each other in a circle.
        record DueOrder(Order order, String reason, String log) {
        }
        List<DueOrder> due = new ArrayList<>();
        for (Long id : possiblyDue) {
            Order order = orderRepository.findLockedById(id).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.PENDING) {
                continue;
            }
            Payment payment = order.getPayment();
            if (payment != null && payment.getPaymentStatus() == PaymentStatus.PAID) {
                continue;
            }
            if (payment != null && payment.getPaymentMethod() == PaymentMethod.INSTALLMENT) {
                // V24: an instalment order waits for the staff first, then for the down payment; each wait has its own clock
                InstallmentApplication application = installments.findByOrderId(order.getId()).orElse(null);
                boolean approved = application != null && InstallmentApplication.APPROVED.equals(application.getStatus()) && application.getReviewedAt() != null;
                LocalDateTime since = approved ? application.getReviewedAt() : order.getCreatedAt();
                if (!since.isBefore(now.minus(approved ? installmentDownPaymentHold : installmentReviewHold))) {
                    continue;
                }
                due.add(new DueOrder(order, approved
                    ? "Tự động hủy: quá hạn thanh toán khoản trả trước của hồ sơ trả góp."
                    : "Tự động hủy: cửa hàng chưa duyệt hồ sơ trả góp trong thời hạn giữ hàng.",
                    approved ? "Expired before down payment" : "Expired before review"));
                continue;
            }
            // VNPay orders have no payment row until the first payment URL is created.
            boolean online = payment == null || isOnline(payment.getPaymentMethod());
            if (!order.getCreatedAt().isBefore(online ? vnpayCutoff : payLaterCutoff)) {
                continue;
            }
            String reason = online
                ? "Tự động hủy: quá hạn thanh toán online."
                : "Tự động hủy: cửa hàng chưa xác nhận đơn trả sau trong thời hạn giữ hàng.";
            due.add(new DueOrder(order, reason, "Expired before payment"));
        }
        // V26: the bikes and colours of every due order are locked together, in one fixed order, before the first one is put back; locking them order by
        // order would take them in whatever order the orders list them and could wait in a circle with a customer ordering several bikes at once
        stockLedger.lock(
            due.stream().flatMap(item -> item.order().getItems().stream()).map(OrderItem::getProduct).filter(Objects::nonNull).toList(),
            due.stream().flatMap(item -> item.order().getItems().stream()).map(OrderItem::getProductVariant).filter(Objects::nonNull).toList());
        List<Order> expired = new ArrayList<>();
        for (DueOrder item : due) {
            cancelOrder(item.order(), item.reason(), null, item.log());
            expired.add(item.order());
        }
        orderRepository.saveAll(expired);
        return expired.size();
    }

    /** An order with no payment row yet, or paid by VNPay, is waiting for an online payment. */
    private static boolean isOnline(PaymentMethod method) {
        return method == null || method == PaymentMethod.VNPAY;
    }

    /**
     * Staff change the quantity of one line while the order is still waiting for confirmation and is paid on hand-over (an online or instalment
     * order has a payment or a plan that this would put out of step). The unit price stays as it was when the order was placed; stock, the ledger, the
     * subtotal, the delivery and registration fees (at the order's own rate), the discount and the payment amount follow.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = {
        com.ebike.config.CacheNames.PRODUCT_LIST_CACHE,
        com.ebike.config.CacheNames.PRODUCT_DETAIL_CACHE
    }, allEntries = true)
    public OrderResponse updateItemQuantity(Long orderId, Long itemId, Integer quantity, Authentication authentication) {
        if (quantity == null || quantity < 1 || quantity > MAX_ITEM_QUANTITY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng mỗi xe phải từ 1 đến " + MAX_ITEM_QUANTITY + ".");
        }
        // the order row is locked first (as in a status change), so a cancel, a confirm or a second edit cannot run between the checks and the save
        Order order = orderRepository.findLockedById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ sửa được số lượng khi đơn đang chờ xác nhận.");
        }
        Payment payment = order.getPayment();
        if (payment == null || payment.getPaymentMethod() != PaymentMethod.PAY_LATER || payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn thanh toán online hoặc trả góp không sửa được số lượng.");
        }
        if (order.getCancellationRequestedAt() != null && order.getCancellationReviewedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn đang có yêu cầu hủy chưa xử lý nên không sửa được số lượng.");
        }
        OrderItem item = order.getItems().stream().filter(line -> itemId.equals(line.getId())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mặt hàng trong đơn."));
        if (!item.getUnits().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Xe này đã có số khung và số máy nên không sửa được số lượng.");
        }
        int before = item.getQuantity();
        int delta = quantity - before;
        if (delta == 0) {
            return toResponse(order);
        }

        Product product = item.getProduct();
        ProductVariant variant = item.getProductVariant();
        stockLedger.lock(product == null ? List.of() : List.of(product), variant == null ? List.of() : List.of(variant));
        if (delta > 0) {
            if (product == null || !Boolean.TRUE.equals(product.getActive()) || (variant != null && !Boolean.TRUE.equals(variant.getActive()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Xe này không còn bán nên không tăng được số lượng.");
            }
            if (product != null && defaultStock(product.getStockQuantity()) < delta) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đủ hàng trong kho: " + item.getProductName());
            }
            if (variant != null && defaultStock(variant.getStockQuantity()) < delta) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Màu xe đã chọn không đủ hàng trong kho: " + variant.getColorName());
            }
        }
        if (product != null) {
            product.setStockQuantity(defaultStock(product.getStockQuantity()) - delta);
        }
        boolean backInStock = false;
        if (variant != null) {
            Integer variantBefore = variant.getStockQuantity();
            variant.setStockQuantity(defaultStock(variantBefore) - delta);
            backInStock = delta < 0 && StockAlertRules.isRestock(variantBefore, variant.getStockQuantity());
        }
        if (product != null) {
            stockLedger.record(List.of(StockLedger.Entry.of(product, variant, -delta, delta > 0 ? StockMovementReason.SALE : StockMovementReason.CANCEL_RESTORE, "Sửa số lượng " + before + " → " + quantity)),
                order.getId(), order.getOrderNumber(), null, getAuthenticatedUserOrNull(authentication));
        }

        // the fees that depend on the number of bikes keep the rate the order was placed at
        int registrableBefore = registrableBikes(order, null, 0);
        boolean keepRegistrationFee = order.getRegistrationFee() != null && order.getRegistrationFee().signum() > 0 && registrableBefore == 0;
        BigDecimal registrationPerBike = order.getRegistrationFee() != null && order.getRegistrationFee().signum() > 0 && registrableBefore > 0
            ? order.getRegistrationFee().divide(BigDecimal.valueOf(registrableBefore), 2, RoundingMode.HALF_UP)
            : ZERO;
        int bikesBefore = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        BigDecimal shippingPerBike = order.getShippingFee() != null && order.getShippingFee().signum() > 0 && bikesBefore > 0
            ? order.getShippingFee().divide(BigDecimal.valueOf(bikesBefore), 2, RoundingMode.HALF_UP)
            : ZERO;

        BigDecimal subtotalBefore = defaultValue(order.getSubtotal());
        item.setQuantity(quantity);
        item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        BigDecimal subtotal = order.getItems().stream().map(OrderItem::getLineTotal).reduce(ZERO, BigDecimal::add);
        order.setSubtotal(subtotal);
        int bikesAfter = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        order.setShippingFee(shippingPerBike.multiply(BigDecimal.valueOf(bikesAfter)));
        if (Boolean.TRUE.equals(order.getIncludeRegistrationService()) && !keepRegistrationFee) {
            order.setRegistrationFee(registrationPerBike.multiply(BigDecimal.valueOf(registrableBikes(order, null, 0))));
        }
        if (defaultValue(order.getDiscountAmount()).signum() > 0) {
            // the terms the order was placed with: a percentage keeps its share of the subtotal (and stays under the promotion's cap),
            // a fixed amount stays; never more than the subtotal. A promotion deleted since then counts as a fixed amount.
            BigDecimal given = defaultValue(order.getDiscountAmount());
            Promotion promotion = order.getPromotion();
            BigDecimal discount = given;
            if (promotion != null && "PERCENTAGE".equals(promotion.getDiscountType()) && subtotalBefore.signum() > 0) {
                discount = given.multiply(subtotal).divide(subtotalBefore, 0, RoundingMode.HALF_UP);
                if (promotion.getMaxDiscountAmount() != null) {
                    discount = discount.min(promotion.getMaxDiscountAmount());
                }
            }
            order.setDiscountAmount(discount.min(subtotal));
        }
        order.setTotalAmount(calculateTotalAmount(subtotal, order.getShippingFee(), order.getDiscountAmount(), order.getRegistrationFee()));
        payment.setAmount(order.getTotalAmount());
        Order saved = orderRepository.save(order);
        if (backInStock) {
            stockAlerts.notifyRestocked(variant);
        }
        auditLog.record("sửa số lượng đơn hàng", order.getOrderNumber() + ": " + item.getProductName() + " " + before + " → " + quantity);
        return toResponse(saved);
    }

    /** Bikes of the order that need a plate (accessories and batteries do not); the product is read from the line, not looked up as an active one. */
    private int registrableBikes(Order order, OrderItem ignored, int unused) {
        int bikes = 0;
        for (OrderItem line : order.getItems()) {
            if (line.getProduct() != null && VehicleRegistration.requiresRegistration(line.getProduct().getSpecification())) {
                bikes += line.getQuantity();
            }
        }
        return bikes;
    }

    @Override
    @Transactional
    public GuestOrderLinkResponse linkGuestOrders(GuestOrderLinkRequest request, Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        // The OTP proves the customer owns this mailbox; without it anyone could register with a stranger's email.
        orderEmailVerificationService.assertVerifiedForOrder(
            request == null ? null : request.emailVerificationSessionId(),
            currentUser.getEmail()
        );

        List<Order> guestOrders = orderRepository.findByUserIsNullAndCustomerEmail(normalizeEmail(currentUser.getEmail()));
        guestOrders.forEach(order -> order.setUser(currentUser));
        orderRepository.saveAll(guestOrders);
        return new GuestOrderLinkResponse(guestOrders.size());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
        com.ebike.config.CacheNames.PRODUCT_LIST_CACHE,
        com.ebike.config.CacheNames.PRODUCT_DETAIL_CACHE
    }, allEntries = true)
    public OrderResponse requestCancellation(Long id, OrderCancellationRequest request, Authentication authentication) {
        // the order row is locked first (before the account is read), see OrderRepository.findLockedById
        Order order = orderRepository.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."));
        User currentUser = getAuthenticatedUser(authentication);

        if (order.getUser() == null || !order.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn chỉ hủy được đơn hàng của mình.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng này đã được hủy.");
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn đã giao không hủy được.");
        }
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn đang được giao không hủy được.");
        }
        if (!isCancellationEligibleStatus(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng này không còn hủy được.");
        }
        if (isShipmentInTransitOrDelivered(order.getShipment())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn đang giao hoặc đã giao không hủy được.");
        }

        cancelOrder(order, request == null ? null : request.reason(), currentUser, "Cancelled by customer");

        return toResponse(orderRepository.save(order));
    }

    private void cancelOrder(Order order, String rawReason, User actor, String paymentNote) {
        String reason = normalizeOptional(rawReason);
        if (reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do hủy.");
        }
        if (reason.length() > MAX_CANCELLATION_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do hủy không được quá " + MAX_CANCELLATION_REASON_LENGTH + " ký tự.");
        }

        order.setCancellationRequestedFromStatus(order.getStatus());
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setCancellationRequestedBy(actor);
        order.setCancellationRequestedAt(java.time.LocalDateTime.now());
        order.setCancellationReviewNote(null);
        order.setCancellationReviewedAt(null);
        // the vehicles go back on sale, so their frame numbers must be free to record on another order
        order.getItems().forEach(item -> item.getUnits().clear());
        cancelPendingPayment(order.getPayment(), paymentNote);
        // V24: an instalment application that is still open closes with its order (a rejected one keeps its verdict)
        if (order.getId() != null) {
            installments.cancelOpenOfOrder(order.getId(), LocalDateTime.now());
        }
        // V26: only a member of the shop's staff is written on the ledger line; the customer who cancels their own order, or the timer, is not
        boolean byStaff = actor != null && (order.getUser() == null || !Objects.equals(order.getUser().getId(), actor.getId()));
        restoreStock(order, byStaff ? actor : null);
        if (order.getPromotion() != null) {
            promotionRepository.releaseUsage(order.getPromotion().getId());
        }
    }

    private void restoreStock(Order order, User ledgerActor) {
        java.util.List<ProductVariant> backInStock = new java.util.ArrayList<>();
        // V26: the rows are locked first (re-read from the database, in a fixed order) so a sale or a receipt at the same moment is not overwritten
        stockLedger.lock(
            order.getItems().stream().map(OrderItem::getProduct).filter(Objects::nonNull).toList(),
            order.getItems().stream().map(OrderItem::getProductVariant).filter(Objects::nonNull).toList());
        List<StockLedger.Entry> stockEntries = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product != null) {
                product.setStockQuantity(defaultStock(product.getStockQuantity()) + item.getQuantity());
            }
            ProductVariant variant = item.getProductVariant();
            if (variant != null) {
                Integer before = variant.getStockQuantity();
                variant.setStockQuantity(defaultStock(before) + item.getQuantity());
                if (StockAlertRules.isRestock(before, variant.getStockQuantity()) && !backInStock.contains(variant)) {
                    backInStock.add(variant);
                }
            }
            if (product != null) {
                stockEntries.add(StockLedger.Entry.of(product, variant, item.getQuantity(), StockMovementReason.CANCEL_RESTORE, null));
            }
        }
        stockLedger.record(stockEntries, order.getId(), order.getOrderNumber(), null, ledgerActor);
        // V22: a cancelled order can bring a sold-out colour back, so customers waiting for it hear about it too.
        backInStock.forEach(stockAlerts::notifyRestocked);
    }

    private int defaultStock(Integer stock) {
        return stock == null ? 0 : stock;
    }

    private void cancelPendingPayment(Payment payment, String note) {
        if (payment == null) {
            return;
        }
        // a payment that failed (a card attempt that did not go through) is closed with its order too, so the staff do not see "failed" on an order that no longer exists
        if (payment.getPaymentStatus() == PaymentStatus.PENDING || payment.getPaymentStatus() == PaymentStatus.FAILED) {
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
            payment.setProviderResponse(note);
        }
    }

    private boolean isCancellationEligibleStatus(OrderStatus status) {
        return status == OrderStatus.PENDING
            || status == OrderStatus.CONFIRMED
            || status == OrderStatus.PROCESSING;
    }

    private boolean isShipmentInTransitOrDelivered(Shipment shipment) {
        if (shipment == null) {
            return false;
        }
        ShipmentStatus shipmentStatus = shipment.getShipmentStatus();
        return shipmentStatus == ShipmentStatus.SHIPPED
            || shipmentStatus == ShipmentStatus.IN_TRANSIT
            || shipmentStatus == ShipmentStatus.DELIVERED;
    }

    private void validateCheckoutDetails(OrderCreateRequest request) {
        String customerName = normalize(request.customerName());
        String phoneNumber = normalizeDigits(request.phoneNumber());
        String customerEmail = normalizeEmail(request.customerEmail());
        String detailedAddress = normalize(request.detailedAddress());
        String notes = normalizeOptional(request.notes());

        if (isBlank(customerName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập họ và tên.");
        }
        if (customerName.length() > MAX_CUSTOMER_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ và tên không được vượt quá " + MAX_CUSTOMER_NAME_LENGTH + " ký tự.");
        }
        if (customerName.chars().anyMatch(Character::isDigit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ và tên không được chứa số.");
        }
        if (isBlank(phoneNumber)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập số điện thoại.");
        }
        if (!VIETNAM_PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại chưa hợp lệ. Vui lòng nhập số Việt Nam bắt đầu bằng 0 và gồm 10-11 chữ số.");
        }
        if (isBlank(customerEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập email.");
        }
        if (!EMAIL_PATTERN.matcher(customerEmail).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email chưa hợp lệ. Ví dụ đúng: khachhang@example.com.");
        }
        boolean homeDelivery = isHomeDelivery(request);
        if (homeDelivery && isBlank(detailedAddress)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập địa chỉ cụ thể.");
        }
        if (homeDelivery && detailedAddress.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ cụ thể quá ngắn. Vui lòng nhập số nhà, tên đường và phường/xã.");
        }
        if (detailedAddress != null && detailedAddress.length() > MAX_DETAILED_ADDRESS_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ cụ thể không được vượt quá " + MAX_DETAILED_ADDRESS_LENGTH + " ký tự.");
        }
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú không được vượt quá " + MAX_NOTES_LENGTH + " ký tự.");
        }
        if (homeDelivery && (isBlank(request.province()) || request.province().trim().length() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn tỉnh/thành phố nhận hàng hợp lệ.");
        }
        if (!homeDelivery && request.pickupShowroomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn showroom nhận xe.");
        }
        if (!homeDelivery && request.pickupShowroomId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Showroom nhận xe không hợp lệ. Vui lòng chọn lại.");
        }
    }

    private boolean isHomeDelivery(OrderCreateRequest request) {
        return isHomeDelivery(request.deliveryMethod());
    }

    private boolean isHomeDelivery(String method) {
        if (method == null || method.isBlank() || "STORE_PICKUP".equals(method)) {
            return false;
        }
        if ("HOME_DELIVERY".equals(method)) {
            return true;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hình thức nhận hàng không hợp lệ.");
    }

    private BigDecimal addItems(Order order, List<OrderCreateItemRequest> items, List<StockLedger.Entry> stockEntries) {
        BigDecimal subtotal = ZERO;
        // V26: the bikes and colours are found first and locked together (in a fixed order, re-read from the database) before any figure is read,
        // so two customers ordering the last bike at the same moment cannot both take it
        List<Product> bikes = new ArrayList<>();
        List<ProductVariant> colours = new ArrayList<>();
        for (OrderCreateItemRequest itemRequest : items) {
            Product product = getActiveProduct(itemRequest.productId());
            bikes.add(product);
            ProductVariant variant = getActiveVariant(product, itemRequest.productVariantId());
            if (variant != null) {
                colours.add(variant);
            }
        }
        stockLedger.lock(bikes, colours);
        for (OrderCreateItemRequest itemRequest : items) {
            Product product = getActiveProduct(itemRequest.productId());
            ProductVariant variant = getActiveVariant(product, itemRequest.productVariantId());
            int currentStockQuantity = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            if (currentStockQuantity < itemRequest.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm đã hết hàng: " + product.getName());
            }
            product.setStockQuantity(currentStockQuantity - itemRequest.quantity());
            if (variant != null) {
                int variantStock = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
                if (variantStock < itemRequest.quantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Màu xe đã chọn đã hết hàng: " + variant.getColorName());
                }
                variant.setStockQuantity(variantStock - itemRequest.quantity());
            }
            stockEntries.add(StockLedger.Entry.of(product, variant, -itemRequest.quantity(), StockMovementReason.SALE, null));

            BigDecimal unitPrice = product.getDiscountPrice() != null ? product.getDiscountPrice() : product.getPrice();
            if (variant != null) {
                unitPrice = unitPrice.add(defaultValue(variant.getAdditionalPrice()));
            }
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity()));

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setProductVariant(variant);
            item.setProductName(product.getName());
            item.setUnitPrice(unitPrice);
            item.setQuantity(itemRequest.quantity());
            item.setLineTotal(lineTotal);

            order.getItems().add(item);
            subtotal = subtotal.add(lineTotal);
        }
        return subtotal;
    }

    private Product getActiveProduct(Long productId) {
        return productRepository.findById(productId)
            .filter(product -> Boolean.TRUE.equals(product.getActive()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm: " + productId));
    }

    private ProductVariant getActiveVariant(Product product, Long variantId) {
        if (variantId == null) {
            if (product.getVariants().stream().anyMatch(variant -> Boolean.TRUE.equals(variant.getActive()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn màu xe trước khi đặt hàng.");
            }
            return null;
        }
        return product.getVariants().stream()
            .filter(variant -> variantId.equals(variant.getId()))
            .filter(variant -> Boolean.TRUE.equals(variant.getActive()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Màu xe đã chọn không có cho sản phẩm này."));
    }

    private Shipment buildShipment(Order order, Showroom showroom, OrderCreateRequest request) {
        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setRecipientName(normalize(request.customerName()));
        shipment.setPhoneNumber(normalizeDigits(request.phoneNumber()));
        shipment.setRecipientEmail(normalizeEmail(request.customerEmail()));
        shipment.setPickupDistrict(showroom == null ? normalize(request.province()) : showroom.getDistrict());
        shipment.setDetailedAddress(showroom == null ? normalize(request.detailedAddress()) : showroom.getAddress());
        shipment.setPickupShowroom(showroom);
        shipment.setShippingAddress(showroom == null
            ? normalize(request.detailedAddress()) + ", " + normalize(request.province())
            : showroom.getName() + " - " + showroom.getAddress());
        return shipment;
    }

    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "ORD-" + datePart + "-" + suffix;
    }

    private BigDecimal defaultValue(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private List<OrderCreateItemRequest> validateQuoteItems(List<OrderCreateItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng cần có ít nhất một xe.");
        }
        if (items.size() > MAX_ORDER_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng chỉ được có tối đa " + MAX_ORDER_ITEMS + " sản phẩm.");
        }

        for (OrderCreateItemRequest itemRequest : items) {
            if (itemRequest == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm trong đơn hàng không hợp lệ.");
            }
            if (itemRequest.productId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã sản phẩm trong đơn hàng.");
            }
            if (itemRequest.productId() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã sản phẩm trong đơn hàng không hợp lệ.");
            }
            if (itemRequest.quantity() == null || itemRequest.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng sản phẩm phải lớn hơn 0.");
            }
            if (itemRequest.quantity() > MAX_ITEM_QUANTITY) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng mỗi sản phẩm không được vượt quá " + MAX_ITEM_QUANTITY + ".");
            }
        }

        return items;
    }

    private OrderQuoteResponse buildQuote(
        List<OrderCreateItemRequest> items,
        boolean includeRegistrationService,
        String discountCode,
        boolean homeDelivery,
        String province
    ) {
        BigDecimal subtotal = ZERO;
        Map<Long, Integer> productQuantities = new HashMap<>();
        Map<Long, Integer> variantQuantities = new HashMap<>();

        for (OrderCreateItemRequest itemRequest : items) {
            Product product = getActiveProduct(itemRequest.productId());
            ProductVariant variant = getActiveVariant(product, itemRequest.productVariantId());
            int productQuantity = productQuantities.merge(product.getId(), itemRequest.quantity(), Integer::sum);
            int variantQuantity = variant == null ? 0 : variantQuantities.merge(variant.getId(), itemRequest.quantity(), Integer::sum);
            if (product.getStockQuantity() == null || product.getStockQuantity() < productQuantity
                || (variant != null && (variant.getStockQuantity() == null || variant.getStockQuantity() < variantQuantity))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số lượng xe vượt quá tồn kho hiện tại: " + product.getName());
            }
            BigDecimal unitPrice = product.getDiscountPrice() != null ? product.getDiscountPrice() : product.getPrice();
            if (variant != null) {
                unitPrice = unitPrice.add(defaultValue(variant.getAdditionalPrice()));
            }
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }

        // The quote is only a preview: a tick on a service the admin has since switched off is ignored here
        // (checkout then unticks and hides it); placing the order still rejects it.
        boolean registrationServiceOffered = !homeDelivery && registrationServiceOffered(items);
        BigDecimal promoDiscountAmount = resolvePromoDiscount(discountCode, subtotal);
        BigDecimal registrationServicePerBike = pricingService.registrationServicePerBike();
        BigDecimal registrationFee = registrationFee(includeRegistrationService && registrationServiceOffered, registrationServicePerBike, items);
        BigDecimal totalDiscountAmount = promoDiscountAmount;
        BigDecimal shippingFee = shippingFee(homeDelivery, province, items);

        return new OrderQuoteResponse(
            subtotal,
            shippingFee,
            totalDiscountAmount,
            registrationFee,
            calculateTotalAmount(subtotal, shippingFee, totalDiscountAmount, registrationFee),
            registrationServicePerBike,
            registrationServiceOffered,
            percentOf(subtotal, pricingService.registrationTaxRateOther()),
            percentOf(subtotal, pricingService.registrationTaxRateCentral()),
            registrableBikes(items) > 0
        );
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP);
    }

    /** The store offers the service, and at least one bike in the order must be registered (pickup is checked by callers). */
    private boolean registrationServiceOffered(List<OrderCreateItemRequest> items) {
        return pricingService.registrationServiceOffered() && registrableBikes(items) > 0;
    }

    private void assertRegistrationServiceOffered(boolean requested, List<OrderCreateItemRequest> items, boolean homeDelivery) {
        if (!requested) {
            return;
        }
        if (!pricingService.registrationServiceOffered()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cửa hàng hiện không nhận hỗ trợ đăng ký xe.");
        }
        if (homeDelivery) {
            // The owner declares the bike on their own VNeID account, so the store can only help in person.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hỗ trợ đăng ký biển số chỉ áp dụng khi nhận xe tại cửa hàng.");
        }
        if (registrableBikes(items) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Xe trong đơn không thuộc diện phải đăng ký biển số.");
        }
    }

    /** Bikes in the order that must be registered (see {@link VehicleRegistration}). */
    private int registrableBikes(List<OrderCreateItemRequest> items) {
        int bikes = 0;
        for (OrderCreateItemRequest item : items) {
            if (VehicleRegistration.requiresRegistration(getActiveProduct(item.productId()).getSpecification())) {
                bikes += item.quantity();
            }
        }
        return bikes;
    }

    /**
     * Pickup at a store is free. Home delivery is charged per bike: the same-city fee when the province has
     * an active showroom, the other-province fee otherwise. Until a province is chosen nothing is charged.
     */
    private BigDecimal shippingFee(boolean homeDelivery, String province, List<OrderCreateItemRequest> items) {
        if (!homeDelivery || isBlank(province)) {
            return ZERO;
        }
        String wanted = provinceKey(province);
        boolean hasShowroom = showroomRepository.findByActiveTrueOrderByDistrictAscNameAsc().stream()
            .anyMatch(showroom -> provinceKey(showroom.getCity()).equals(wanted));
        int bikes = items.stream().mapToInt(OrderCreateItemRequest::quantity).sum();
        return pricingService.homeDeliveryPerBike(hasShowroom).multiply(BigDecimal.valueOf(bikes));
    }

    /** "Thành phố Hồ Chí Minh", "TP. Ho Chi Minh" and "Hồ Chí Minh" all become "ho chi minh". */
    private static String provinceKey(String name) {
        if (name == null) {
            return "";
        }
        String plain = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace("đ", "d").replace("Đ", "D")
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return plain.replaceFirst("^(thanh pho|tinh|tp) ", "");
    }

    /** Each bike is registered on its own papers, so the fee is charged per bike that needs a plate. */
    private BigDecimal registrationFee(boolean includeRegistrationService, BigDecimal perBike, List<OrderCreateItemRequest> items) {
        if (!includeRegistrationService) {
            return ZERO;
        }
        return perBike.multiply(BigDecimal.valueOf(registrableBikes(items)));
    }

    private boolean shouldIncludeRegistrationService(Boolean includeRegistrationService) {
        return Boolean.TRUE.equals(includeRegistrationService);
    }

    private BigDecimal calculateTotalAmount(
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal discountAmount,
        BigDecimal registrationFee
    ) {
        BigDecimal totalAmount = defaultValue(subtotal)
            .add(defaultValue(shippingFee))
            .add(defaultValue(registrationFee))
            .subtract(defaultValue(discountAmount));

        return totalAmount.signum() < 0 ? ZERO : totalAmount;
    }

    /** One use per customer: a signed-in customer by account, a guest by email. Cancelled orders do not count. */
    private void assertCodeNotUsedBefore(Promotion promotion, User user, String email) {
        boolean used = user != null
            ? orderRepository.existsByUserIdAndPromotionIdAndStatusNot(user.getId(), promotion.getId(), OrderStatus.CANCELLED)
            : orderRepository.existsByUserIsNullAndCustomerEmailIgnoreCaseAndPromotionIdAndStatusNot(email, promotion.getId(), OrderStatus.CANCELLED);
        if (used) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn đã dùng mã giảm giá này cho một đơn hàng khác.");
        }
    }

    private BigDecimal resolvePromoDiscount(String discountCode, BigDecimal subtotal) {
        Promotion promotion = findValidPromotion(discountCode);
        return promotion == null ? ZERO : calculatePromoDiscount(promotion, subtotal);
    }

    /** Returns the usable promotion for the code, null when no code was given; throws 400 when the code is not usable. */
    private Promotion findValidPromotion(String discountCode) {
        if (discountCode == null || discountCode.isBlank()) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        return promotionRepository.findByCodeIgnoreCase(discountCode.trim())
            .filter(promotion -> "LIVE".equals(promotion.getStatus()))
            .filter(promotion -> promotion.getStartsAt() == null || !promotion.getStartsAt().isAfter(now))
            .filter(promotion -> promotion.getEndsAt() == null || !promotion.getEndsAt().isBefore(now))
            .filter(promotion -> promotion.getUsageLimit() == null
                || promotion.getUsageLimit() == 0
                || promotion.getUsageCount() < promotion.getUsageLimit())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá không hợp lệ hoặc đã hết hạn."));
    }

    private BigDecimal calculatePromoDiscount(Promotion promotion, BigDecimal subtotal) {
        BigDecimal base = defaultValue(subtotal);
        BigDecimal discount;
        if ("PERCENTAGE".equals(promotion.getDiscountType())) {
            discount = base.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            if (promotion.getMaxDiscountAmount() != null) {
                discount = discount.min(promotion.getMaxDiscountAmount());
            }
        } else {
            discount = promotion.getDiscountValue();
        }
        return discount.min(base);
    }

    private void attachInitialPayment(Order order, String rawPaymentMethod, InstallmentPlanner.Prepared installment) {
        PaymentMethod paymentMethod = parseCheckoutPaymentMethod(rawPaymentMethod);
        if (paymentMethod == PaymentMethod.VNPAY) {
            return;
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(installment != null ? PaymentMethod.INSTALLMENT : PaymentMethod.PAY_LATER);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        // an instalment order is paid in two parts: this row is the down payment, the rest is the application's plan
        payment.setAmount(installment != null ? installment.plan().downPayment() : order.getTotalAmount());
        payment.setCurrency("VND");
        order.setPayment(payment);
    }

    /**
     * V24: an order paid by instalments locks the customer's row before anything else is read. The limit of open applications is counted under that
     * lock, and under REPEATABLE READ a plain SELECT made earlier would already have fixed a snapshot that cannot show an application another order
     * committed while this one waited (the test-ride booking does the same).
     */
    private void lockCustomerForInstallment(OrderCreateRequest request, Authentication authentication) {
        if (request.paymentMethod() == null || !PaymentMethod.INSTALLMENT.name().equalsIgnoreCase(request.paymentMethod().trim())
            || authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return;
        }
        userRepository.findLockedByUsername(authentication.getName());
    }

    /** V24: an instalment application is a customer's; Manager and Admin accounts can place orders too, but not apply for credit. */
    private void assertCustomerRole(Authentication authentication) {
        // only a pure customer account: one that also holds a staff role may not apply for credit
        boolean customer = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()))
            && authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().startsWith("ROLE_") && !"ROLE_CUSTOMER".equals(authority.getAuthority()));
        if (authentication != null && !customer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ tài khoản khách hàng mới mua trả góp được.");
        }
    }

    private PaymentMethod parseCheckoutPaymentMethod(String rawPaymentMethod) {
        if (rawPaymentMethod == null || rawPaymentMethod.isBlank()) {
            return PaymentMethod.PAY_LATER;
        }
        try {
            PaymentMethod paymentMethod = PaymentMethod.valueOf(rawPaymentMethod.trim().toUpperCase(Locale.ROOT));
            if (paymentMethod == PaymentMethod.PAY_LATER || paymentMethod == PaymentMethod.VNPAY || paymentMethod == PaymentMethod.INSTALLMENT) {
                return paymentMethod;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to the checkout-specific validation message below.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phương thức thanh toán phải là PAY_LATER, VNPAY hoặc INSTALLMENT.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean matchesOrderStatus(Order order, String rawStatus) {
        if (isBlank(rawStatus)) {
            return true;
        }
        try {
            OrderStatus status = OrderStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
            return order.getStatus() == status;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc trạng thái đơn hàng không hợp lệ.");
        }
    }

    private boolean matchesPaymentStatus(Order order, String rawPaymentStatus) {
        if (isBlank(rawPaymentStatus)) {
            return true;
        }
        Payment payment = order.getPayment();
        if (payment == null) {
            return false;
        }
        try {
            PaymentStatus paymentStatus = PaymentStatus.valueOf(rawPaymentStatus.trim().toUpperCase(Locale.ROOT));
            return payment.getPaymentStatus() == paymentStatus;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc trạng thái thanh toán không hợp lệ.");
        }
    }

    private boolean matchesSearch(Order order, String rawSearch) {
        if (isBlank(rawSearch)) {
            return true;
        }
        String search = rawSearch.trim().toLowerCase(Locale.ROOT);
        Shipment shipment = order.getShipment();
        String customerName = shipment == null ? null : shipment.getRecipientName();
        String phoneNumber = shipment == null ? null : shipment.getPhoneNumber();

        return containsIgnoreCase(order.getOrderNumber(), search)
            || containsIgnoreCase(order.getCustomerEmail(), search)
            || containsIgnoreCase(customerName, search)
            || containsIgnoreCase(phoneNumber, search)
            || hasUnitMatching(order, squash(search));
    }

    // V18: find the order a vehicle was sold on from its frame or engine number (warranty, recall, registration).
    private boolean hasUnitMatching(Order order, String search) {
        return !search.isEmpty() && order.getItems().stream()
            .flatMap(item -> item.getUnits().stream())
            .anyMatch(unit -> squash(unit.getFrameNumber()).contains(search) || squash(unit.getEngineNumber()).contains(search));
    }

    // Spaces and hyphens are ignored on both sides, so "rlh abc" finds RLH-ABC12345.
    private static String squash(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private boolean matchesShowroom(Order order, Long showroomId) {
        if (showroomId == null) {
            return true;
        }
        Shipment shipment = order.getShipment();
        Showroom showroom = shipment == null ? null : shipment.getPickupShowroom();
        return showroom != null && showroomId.equals(showroom.getId());
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String normalizeOptional(String value) {
        return normalize(value);
    }

    private String normalizeEmail(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeDigits(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.replaceAll("[\\s.-]", "");
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập.");
        }
        return userRepository.findByUsername(authentication.getName())
            .or(() -> userRepository.findByEmail(authentication.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản đang đăng nhập."));
    }

    private void assertCanAccessOrder(Order order, Authentication authentication) {
        if (isBackOffice(authentication)) {
            return;
        }
        User currentUser = getAuthenticatedUser(authentication);
        if (order.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đơn của khách vãng lai chỉ xem được từ trang thanh toán hoặc trang kết quả thanh toán.");
        }
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn chỉ xem được đơn hàng của mình.");
        }
    }

    private boolean isBackOffice(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> PermissionConstants.OrderManagement.ORDER_VIEW_ALL.equals(authority.getAuthority()));
    }

    private User getAuthenticatedUserOrNull(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName())
            .or(() -> userRepository.findByEmail(authentication.getName()))
            .orElse(null);
    }

    private OrderResponse toResponse(Order order) {
        Shipment shipment = order.getShipment();
        Payment payment = order.getPayment();
        return new OrderResponse(
            order.getId(),
            order.getUser() == null ? null : order.getUser().getId(),
            order.getOrderNumber(),
            order.getStatus().name(),
            order.getSubtotal(),
            order.getShippingFee(),
            order.getDiscountAmount(),
            order.getRegistrationFee(),
            Boolean.TRUE.equals(order.getIncludeRegistrationService()),
            order.getTotalAmount(),
            payment == null ? null : payment.getPaymentMethod().name(),
            payment == null ? null : payment.getPaymentStatus().name(),
            order.getNotes(),
            order.getCustomerEmail(),
            order.getCancellationReason(),
            order.getCancellationReviewNote(),
            order.getCancellationRequestedFromStatus() == null ? null : order.getCancellationRequestedFromStatus().name(),
            order.getCancellationRequestedBy() == null ? null : order.getCancellationRequestedBy().getId(),
            order.getCancellationRequestedAt(),
            order.getCancellationReviewedAt(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            order.getPickupDeadlineAt(),
            order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getId))
                .map(item -> new OrderItemResponse(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getLineTotal(),
                    item.getProductVariant() == null ? null : item.getProductVariant().getId(),
                    item.getProductVariant() == null ? null : item.getProductVariant().getColorName(),
                    OrderUnits.requiresNumbers(item),
                    item.getUnits().stream().map(unit -> new OrderItemUnitResponse(unit.getId(), unit.getFrameNumber(), unit.getEngineNumber())).toList()
                ))
                .toList(),
            shipment == null ? null : new ShipmentResponse(
                shipment.getId(),
                shipment.getShipmentStatus().name(),
                shipment.getRecipientName(),
                shipment.getPhoneNumber(),
                shipment.getRecipientEmail(),
                shipment.getPickupDistrict(),
                shipment.getDetailedAddress(),
                shipment.getShippingAddress(),
                shipment.getTrackingNumber(),
                shipment.getShippedAt(),
                shipment.getDeliveredAt(),
                shipment.getPickupShowroom() == null ? null : new ShowroomResponse(
                    shipment.getPickupShowroom().getId(),
                    shipment.getPickupShowroom().getName(),
                    shipment.getPickupShowroom().getCity(),
                    shipment.getPickupShowroom().getDistrict(),
                    shipment.getPickupShowroom().getAddress(),
                    shipment.getPickupShowroom().getPhone(),
                    shipment.getPickupShowroom().getOpeningHours(),
                    shipment.getPickupShowroom().getActive()
                )
            )
        );
    }
}
