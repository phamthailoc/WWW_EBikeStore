package com.ebike.service.order.impl;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.dto.order.request.OrderCancellationRequest;
import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.request.OrderQuoteRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.dto.order.response.OrderItemResponse;
import com.ebike.dto.order.response.OrderQuoteResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.dto.order.response.ShipmentResponse;
import com.ebike.dto.order.response.ShowroomResponse;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal SHOWROOM_INCENTIVE_AMOUNT = new BigDecimal("1200000");
    private static final BigDecimal REGISTRATION_FEE_AMOUNT = new BigDecimal("2500000");
    private static final int MAX_ORDER_ITEMS = 20;
    private static final int MAX_ITEM_QUANTITY = 10;
    private static final int MAX_CUSTOMER_NAME_LENGTH = 100;
    private static final int MAX_DETAILED_ADDRESS_LENGTH = 500;
    private static final int MAX_NOTES_LENGTH = 1000;
    private static final int MAX_CANCELLATION_REASON_LENGTH = 1000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIETNAM_PHONE_PATTERN = Pattern.compile("^0\\d{9,10}$");
    private static final Map<String, BigDecimal> PROMO_CODE_DISCOUNTS = Map.of(
        "EBIKE100", new BigDecimal("100000"),
        "EBIKE200", new BigDecimal("200000")
    );
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ShowroomRepository showroomRepository;
    private final OrderEmailVerificationService orderEmailVerificationService;

    public OrderServiceImpl(
        OrderRepository orderRepository,
        UserRepository userRepository,
        ProductRepository productRepository,
        ShowroomRepository showroomRepository,
        OrderEmailVerificationService orderEmailVerificationService
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.showroomRepository = showroomRepository;
        this.orderEmailVerificationService = orderEmailVerificationService;
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
        return buildQuote(
            validateQuoteItems(request == null ? null : request.items()),
            shouldIncludeRegistrationService(request == null ? null : request.includeRegistrationService()),
            request == null ? null : request.discountCode()
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
        order.setShippingFee(ZERO);
        boolean includeRegistrationService = shouldIncludeRegistrationService(request.includeRegistrationService());
        order.setIncludeRegistrationService(includeRegistrationService);
        order.setRegistrationFee(includeRegistrationService ? REGISTRATION_FEE_AMOUNT : ZERO);
        validateCheckoutDetails(request);
        if (emailOtpRequired) {
            orderEmailVerificationService.assertVerifiedForOrder(request.emailVerificationSessionId(), request.customerEmail());
        }
        order.setNotes(normalizeOptional(request.notes()));
        order.setCustomerEmail(normalizeEmail(request.customerEmail()));

        Showroom showroom = showroomRepository.findById(request.pickupShowroomId())
            .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pickup showroom not found"));

        BigDecimal subtotal = addItems(order, validateQuoteItems(request.items()));
        order.setSubtotal(subtotal);
        BigDecimal promoDiscountAmount = resolvePromoDiscount(request.discountCode(), subtotal);
        order.setDiscountAmount(SHOWROOM_INCENTIVE_AMOUNT.add(promoDiscountAmount));
        order.setTotalAmount(calculateTotalAmount(subtotal, order.getShippingFee(), order.getDiscountAmount(), order.getRegistrationFee()));
        order.setShipment(buildShipment(order, showroom, request));
        attachInitialPayment(order, request.paymentMethod());

        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request) {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        try {
            order.setStatus(OrderStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order status");
        }

        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
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

        String reason = normalizeOptional(request == null ? null : request.reason());
        if (reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is required");
        }
        if (reason.length() > MAX_CANCELLATION_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason cannot exceed " + MAX_CANCELLATION_REASON_LENGTH + " characters");
        }

        order.setCancellationRequestedFromStatus(order.getStatus());
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setCancellationRequestedBy(currentUser);
        order.setCancellationRequestedAt(java.time.LocalDateTime.now());
        order.setCancellationReviewNote(null);
        order.setCancellationReviewedAt(null);
        cancelPendingPayment(order.getPayment());

        return toResponse(orderRepository.save(order));
    }

    private void cancelPendingPayment(Payment payment) {
        if (payment == null) {
            return;
        }
        if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
            payment.setProviderResponse("Cancelled by customer");
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
        if (isBlank(detailedAddress)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập địa chỉ cụ thể.");
        }
        if (detailedAddress.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ cụ thể quá ngắn. Vui lòng nhập số nhà, tên đường và phường/xã.");
        }
        if (detailedAddress.length() > MAX_DETAILED_ADDRESS_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ cụ thể không được vượt quá " + MAX_DETAILED_ADDRESS_LENGTH + " ký tự.");
        }
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú không được vượt quá " + MAX_NOTES_LENGTH + " ký tự.");
        }
        if (request.pickupShowroomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn showroom nhận xe.");
        }
        if (request.pickupShowroomId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Showroom nhận xe không hợp lệ. Vui lòng chọn lại.");
        }
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
        shipment.setPickupDistrict(showroom.getDistrict());
        shipment.setDetailedAddress(normalize(request.detailedAddress()));
        shipment.setPickupShowroom(showroom);
        shipment.setShippingAddress(buildShippingAddress(showroom, request.detailedAddress()));
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

    private OrderQuoteResponse buildQuote(List<OrderCreateItemRequest> items, boolean includeRegistrationService, String discountCode) {
        BigDecimal subtotal = ZERO;

        for (OrderCreateItemRequest itemRequest : items) {
            Product product = getActiveProduct(itemRequest.productId());
            ProductVariant variant = getActiveVariant(product, itemRequest.productVariantId());
            BigDecimal unitPrice = product.getDiscountPrice() != null ? product.getDiscountPrice() : product.getPrice();
            if (variant != null) {
                unitPrice = unitPrice.add(defaultValue(variant.getAdditionalPrice()));
            }
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }

        BigDecimal promoDiscountAmount = resolvePromoDiscount(discountCode, subtotal);
        BigDecimal registrationFee = includeRegistrationService ? REGISTRATION_FEE_AMOUNT : ZERO;
        BigDecimal totalDiscountAmount = SHOWROOM_INCENTIVE_AMOUNT.add(promoDiscountAmount);

        return new OrderQuoteResponse(
            subtotal,
            ZERO,
            totalDiscountAmount,
            registrationFee,
            calculateTotalAmount(subtotal, ZERO, totalDiscountAmount, registrationFee)
        );
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
        if (discountCode == null || discountCode.isBlank()) {
            return ZERO;
        }

        String normalizedCode = discountCode.trim().toUpperCase(Locale.ROOT);
        BigDecimal promoDiscount = PROMO_CODE_DISCOUNTS.get(normalizedCode);
        if (promoDiscount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giảm giá không hợp lệ hoặc đã hết hạn.");
        }

        return promoDiscount.min(defaultValue(subtotal));
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

    private String buildShippingAddress(Showroom showroom, String detailedAddress) {
        return showroom.getName() + " - " + showroom.getAddress() + " | Dia chi cu the: " + normalize(detailedAddress);
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
