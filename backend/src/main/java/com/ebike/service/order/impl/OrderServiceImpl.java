package com.ebike.service.order.impl;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.dto.order.request.GuestOrderLinkRequest;
import com.ebike.dto.order.request.OrderCancellationRequest;
import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.request.OrderQuoteRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.dto.order.response.OrderItemResponse;
import com.ebike.dto.order.response.GuestOrderLinkResponse;
import com.ebike.dto.order.response.OrderQuoteResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.dto.order.response.ShipmentResponse;
import com.ebike.dto.order.response.ShowroomResponse;
import com.ebike.model.entity.admin.Promotion;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.service.admin.PricingService;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.ShipmentStatus;
import com.ebike.model.entity.order.Showroom;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.service.order.OrderEmailVerificationService;
import com.ebike.service.order.OrderService;
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

    public OrderServiceImpl(
        OrderRepository orderRepository,
        UserRepository userRepository,
        ProductRepository productRepository,
        ShowroomRepository showroomRepository,
        PromotionRepository promotionRepository,
        OrderEmailVerificationService orderEmailVerificationService,
        PricingService pricingService
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.showroomRepository = showroomRepository;
        this.promotionRepository = promotionRepository;
        this.orderEmailVerificationService = orderEmailVerificationService;
        this.pricingService = pricingService;
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
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, Authentication authentication) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
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
        com.ebike.config.RedisCacheConfiguration.PRODUCT_LIST_CACHE,
        com.ebike.config.RedisCacheConfiguration.PRODUCT_DETAIL_CACHE
    }, allEntries = true)
    public OrderResponse createOrder(OrderCreateRequest request, Authentication authentication) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn hàng cần có ít nhất một sản phẩm.");
        }

        User user = getAuthenticatedUserOrNull(authentication);

        Order order = new Order();
        if (user != null) {
            order.setUser(user);
        }
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.PENDING);
        boolean includeRegistrationService = shouldIncludeRegistrationService(request.includeRegistrationService());
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

        List<OrderCreateItemRequest> items = validateQuoteItems(request.items());
        BigDecimal subtotal = addItems(order, items);
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee(isHomeDelivery(request), request.province(), items));
        order.setRegistrationFee(registrationFee(includeRegistrationService, pricingService.registrationServicePerBike(), items));
        Promotion promotion = findValidPromotion(request.discountCode());
        BigDecimal promoDiscountAmount = promotion == null ? ZERO : calculatePromoDiscount(promotion, subtotal);
        if (promotion != null && promotionRepository.tryIncrementUsage(promotion.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá không hợp lệ hoặc đã hết hạn.");
        }
        order.setDiscountAmount(promoDiscountAmount);
        order.setTotalAmount(calculateTotalAmount(subtotal, order.getShippingFee(), order.getDiscountAmount(), order.getRegistrationFee()));
        order.setShipment(buildShipment(order, showroom, request));
        attachInitialPayment(order, request.paymentMethod());

        return toResponse(orderRepository.save(order));
    }

    // The steps a staff member may take from each status. A cancelled or delivered order is final.
    private static final Map<OrderStatus, List<OrderStatus>> STATUS_TRANSITIONS = Map.of(
        OrderStatus.PENDING, List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
        OrderStatus.CONFIRMED, List.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
        OrderStatus.PROCESSING, List.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
        OrderStatus.SHIPPED, List.of(OrderStatus.DELIVERED)
    );

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
        com.ebike.config.RedisCacheConfiguration.PRODUCT_LIST_CACHE,
        com.ebike.config.RedisCacheConfiguration.PRODUCT_DETAIL_CACHE
    }, allEntries = true)
    public OrderResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request, Authentication authentication) {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        OrderStatus nextStatus;
        try {
            nextStatus = OrderStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order status");
        }

        if (!STATUS_TRANSITIONS.getOrDefault(order.getStatus(), List.of()).contains(nextStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot change an order from " + order.getStatus() + " to " + nextStatus);
        }

        if (nextStatus == OrderStatus.CANCELLED) {
            // Same effects as a customer cancelling: reason saved, pending payment closed, stock returned.
            cancelOrder(order, request.reason(), getAuthenticatedUserOrNull(authentication), "Cancelled by staff");
        } else {
            order.setStatus(nextStatus);
        }

        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {
        com.ebike.config.RedisCacheConfiguration.PRODUCT_LIST_CACHE,
        com.ebike.config.RedisCacheConfiguration.PRODUCT_DETAIL_CACHE
    }, allEntries = true)
    public int expireUnpaidOrders(LocalDateTime now) {
        LocalDateTime vnpayCutoff = now.minus(vnpayExpiry);
        LocalDateTime payLaterCutoff = now.minus(payLaterExpiry);
        LocalDateTime latestCutoff = vnpayCutoff.isAfter(payLaterCutoff) ? vnpayCutoff : payLaterCutoff;

        List<Order> expired = new ArrayList<>();
        for (Order order : orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, latestCutoff)) {
            Payment payment = order.getPayment();
            if (payment != null && payment.getPaymentStatus() == PaymentStatus.PAID) {
                continue;
            }
            // VNPay orders have no payment row until the first payment URL is created.
            boolean online = payment == null || payment.getPaymentMethod() == PaymentMethod.VNPAY;
            if (!order.getCreatedAt().isBefore(online ? vnpayCutoff : payLaterCutoff)) {
                continue;
            }
            String reason = online
                ? "Tự động hủy: quá hạn thanh toán online."
                : "Tự động hủy: cửa hàng chưa xác nhận đơn trả sau trong thời hạn giữ hàng.";
            cancelOrder(order, reason, null, "Expired before payment");
            expired.add(order);
        }
        orderRepository.saveAll(expired);
        return expired.size();
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
        com.ebike.config.RedisCacheConfiguration.PRODUCT_LIST_CACHE,
        com.ebike.config.RedisCacheConfiguration.PRODUCT_DETAIL_CACHE
    }, allEntries = true)
    public OrderResponse requestCancellation(Long id, OrderCancellationRequest request, Authentication authentication) {
        User currentUser = getAuthenticatedUser(authentication);
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getUser() == null || !order.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own orders");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This order has already been cancelled");
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivered orders cannot be cancelled");
        }
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Orders that are already shipping cannot be cancelled");
        }
        if (!isCancellationEligibleStatus(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This order is no longer eligible for cancellation");
        }
        if (isShipmentInTransitOrDelivered(order.getShipment())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Orders that are already shipping or delivered cannot be cancelled");
        }

        cancelOrder(order, request == null ? null : request.reason(), currentUser, "Cancelled by customer");

        return toResponse(orderRepository.save(order));
    }

    private void cancelOrder(Order order, String rawReason, User actor, String paymentNote) {
        String reason = normalizeOptional(rawReason);
        if (reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is required");
        }
        if (reason.length() > MAX_CANCELLATION_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason cannot exceed " + MAX_CANCELLATION_REASON_LENGTH + " characters");
        }

        order.setCancellationRequestedFromStatus(order.getStatus());
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setCancellationRequestedBy(actor);
        order.setCancellationRequestedAt(java.time.LocalDateTime.now());
        order.setCancellationReviewNote(null);
        order.setCancellationReviewedAt(null);
        cancelPendingPayment(order.getPayment(), paymentNote);
        restoreStock(order);
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product != null) {
                product.setStockQuantity(defaultStock(product.getStockQuantity()) + item.getQuantity());
            }
            ProductVariant variant = item.getProductVariant();
            if (variant != null) {
                variant.setStockQuantity(defaultStock(variant.getStockQuantity()) + item.getQuantity());
            }
        }
    }

    private int defaultStock(Integer stock) {
        return stock == null ? 0 : stock;
    }

    private void cancelPendingPayment(Payment payment, String note) {
        if (payment == null) {
            return;
        }
        if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
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

    private BigDecimal addItems(Order order, List<OrderCreateItemRequest> items) {
        BigDecimal subtotal = ZERO;
        for (OrderCreateItemRequest itemRequest : items) {
            Product product = getActiveProduct(itemRequest.productId());
            ProductVariant variant = getActiveVariant(product, itemRequest.productVariantId());
            int currentStockQuantity = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            if (currentStockQuantity < itemRequest.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is out of stock: " + product.getName());
            }
            product.setStockQuantity(currentStockQuantity - itemRequest.quantity());
            if (variant != null) {
                int variantStock = variant.getStockQuantity() == null ? 0 : variant.getStockQuantity();
                if (variantStock < itemRequest.quantity()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected color is out of stock: " + variant.getColorName());
                }
                variant.setStockQuantity(variantStock - itemRequest.quantity());
            }

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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId));
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected color is not available for this product"));
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one order item is required");
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

        BigDecimal promoDiscountAmount = resolvePromoDiscount(discountCode, subtotal);
        BigDecimal registrationServicePerBike = pricingService.registrationServicePerBike();
        BigDecimal registrationFee = registrationFee(includeRegistrationService, registrationServicePerBike, items);
        BigDecimal totalDiscountAmount = promoDiscountAmount;
        BigDecimal shippingFee = shippingFee(homeDelivery, province, items);

        return new OrderQuoteResponse(
            subtotal,
            shippingFee,
            totalDiscountAmount,
            registrationFee,
            calculateTotalAmount(subtotal, shippingFee, totalDiscountAmount, registrationFee),
            registrationServicePerBike
        );
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

    /** Each bike is registered on its own papers, so the service fee is charged per bike. */
    private BigDecimal registrationFee(boolean includeRegistrationService, BigDecimal perBike, List<OrderCreateItemRequest> items) {
        if (!includeRegistrationService) {
            return ZERO;
        }
        int bikes = items.stream().mapToInt(OrderCreateItemRequest::quantity).sum();
        return perBike.multiply(BigDecimal.valueOf(bikes));
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

    private void attachInitialPayment(Order order, String rawPaymentMethod) {
        PaymentMethod paymentMethod = parseCheckoutPaymentMethod(rawPaymentMethod);
        if (paymentMethod == PaymentMethod.VNPAY) {
            return;
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.PAY_LATER);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency("VND");
        order.setPayment(payment);
    }

    private PaymentMethod parseCheckoutPaymentMethod(String rawPaymentMethod) {
        if (rawPaymentMethod == null || rawPaymentMethod.isBlank()) {
            return PaymentMethod.PAY_LATER;
        }
        try {
            PaymentMethod paymentMethod = PaymentMethod.valueOf(rawPaymentMethod.trim().toUpperCase(Locale.ROOT));
            if (paymentMethod == PaymentMethod.PAY_LATER || paymentMethod == PaymentMethod.VNPAY) {
                return paymentMethod;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to the checkout-specific validation message below.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method must be PAY_LATER or VNPAY");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order status filter");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment status filter");
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
            || containsIgnoreCase(phoneNumber, search);
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
            .or(() -> userRepository.findByEmail(authentication.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private void assertCanAccessOrder(Order order, Authentication authentication) {
        if (isBackOffice(authentication)) {
            return;
        }
        User currentUser = getAuthenticatedUser(authentication);
        if (order.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest orders can only be accessed from checkout or payment return");
        }
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own orders");
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
                    item.getProductVariant() == null ? null : item.getProductVariant().getColorName()
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
