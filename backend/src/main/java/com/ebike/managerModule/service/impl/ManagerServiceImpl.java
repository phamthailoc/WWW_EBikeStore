package com.ebike.managerModule.service.impl;

import com.ebike.authModule.entity.Role;
import com.ebike.authModule.entity.User;
import com.ebike.authModule.repository.UserRepository;
import com.ebike.config.RedisCacheConfiguration;
import com.ebike.managerModule.dto.request.ManagerPaymentConfirmationRequest;
import com.ebike.managerModule.dto.request.ManagerProductStockUpdateRequest;
import com.ebike.managerModule.dto.request.ManagerShipmentUpdateRequest;
import com.ebike.managerModule.dto.response.ManagerCustomerResponse;
import com.ebike.managerModule.dto.response.ManagerDashboardResponse;
import com.ebike.managerModule.dto.response.ManagerPaymentResponse;
import com.ebike.managerModule.dto.response.ManagerRevenuePeriodPointResponse;
import com.ebike.managerModule.dto.response.ManagerRevenueReportResponse;
import com.ebike.managerModule.dto.response.ManagerTopProductResponse;
import com.ebike.managerModule.service.ManagerService;
import com.ebike.orderModule.dto.request.OrderCancellationRequest;
import com.ebike.orderModule.dto.response.OrderResponse;
import com.ebike.orderModule.entity.Order;
import com.ebike.orderModule.entity.OrderItem;
import com.ebike.orderModule.entity.OrderStatus;
import com.ebike.orderModule.entity.Payment;
import com.ebike.orderModule.entity.PaymentMethod;
import com.ebike.orderModule.entity.PaymentStatus;
import com.ebike.orderModule.entity.Shipment;
import com.ebike.orderModule.entity.ShipmentStatus;
import com.ebike.orderModule.entity.Showroom;
import com.ebike.orderModule.repository.OrderRepository;
import com.ebike.orderModule.repository.PaymentRepository;
import com.ebike.productModule.dto.response.ProductSummaryDto;
import com.ebike.productModule.entity.Product;
import com.ebike.productModule.repository.ProductRepository;
import com.ebike.productModule.service.ProductService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ManagerServiceImpl implements ManagerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String ROLE_CUSTOMER = "CUSTOMER";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    public ManagerServiceImpl(
        OrderRepository orderRepository,
        PaymentRepository paymentRepository,
        UserRepository userRepository,
        ProductRepository productRepository,
        ProductService productService
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productService = productService;
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerDashboardResponse getDashboard() {
        List<Order> orders = orderRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        LocalDate monthStart = today.withDayOfMonth(1);

        long totalOrders = orders.size();
        long pendingOrders = orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PENDING)
            .count();
        long unpaidPayLaterOrders = orders.stream()
            .map(Order::getPayment)
            .filter(payment -> payment != null)
            .filter(payment -> payment.getPaymentMethod() == PaymentMethod.PAY_LATER)
            .filter(payment -> payment.getPaymentStatus() == PaymentStatus.PENDING)
            .count();

        BigDecimal todayRevenue = sumPaidOrdersWithin(orders, today, today);
        BigDecimal weekRevenue = sumPaidOrdersWithin(orders, weekStart, today);
        BigDecimal monthRevenue = sumPaidOrdersWithin(orders, monthStart, today);
        List<OrderResponse> recentOrders = orders.stream()
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .limit(5)
            .map(this::toOrderResponse)
            .toList();

        return new ManagerDashboardResponse(
            totalOrders,
            pendingOrders,
            unpaidPayLaterOrders,
            todayRevenue,
            weekRevenue,
            monthRevenue,
            recentOrders
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerPaymentResponse> getPayments(String paymentStatus, String paymentMethod, String search) {
        return paymentRepository.findAll().stream()
            .filter(payment -> matchesPaymentStatus(payment, paymentStatus))
            .filter(payment -> matchesPaymentMethod(payment, paymentMethod))
            .filter(payment -> matchesPaymentSearch(payment, search))
            .sorted(Comparator.comparing((Payment payment) -> payment.getOrder().getCreatedAt()).reversed())
            .map(this::toPaymentResponse)
            .toList();
    }

    @Override
    @Transactional
    public ManagerPaymentResponse confirmPayLaterPayment(Long paymentId, ManagerPaymentConfirmationRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        if (payment.getPaymentMethod() != PaymentMethod.PAY_LATER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PAY_LATER payments can be confirmed manually");
        }
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment is already marked as paid");
        }
        if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refunded payments cannot be confirmed again");
        }

        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        if (request != null && request.providerTxnId() != null && !request.providerTxnId().isBlank()) {
            payment.setProviderTxnId(request.providerTxnId().trim());
        }
        if (request != null && request.note() != null && !request.note().isBlank()) {
            payment.setProviderResponse(request.note().trim());
        }

        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolve the cancellation request before confirming this payment");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }

        return toPaymentResponse(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public OrderResponse approveOrderCancellation(Long orderId, OrderCancellationRequest request) {
        Order order = getPendingCancellationOrder(orderId);
        Payment payment = order.getPayment();
        if (payment == null || payment.getPaymentMethod() != PaymentMethod.PAY_LATER || payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is no longer eligible for cancellation approval");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReviewNote(normalizeOptional(request == null ? null : request.reviewNote()));
        order.setCancellationReviewedAt(LocalDateTime.now());
        payment.setPaymentStatus(PaymentStatus.CANCELLED);
        payment.setProviderResponse("Cancelled after manager approval");

        return toOrderResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse rejectOrderCancellation(Long orderId, OrderCancellationRequest request) {
        Order order = getPendingCancellationOrder(orderId);
        OrderStatus previousStatus = order.getCancellationRequestedFromStatus() == null
            ? OrderStatus.PENDING
            : order.getCancellationRequestedFromStatus();

        order.setStatus(previousStatus);
        order.setCancellationReviewNote(normalizeOptional(request == null ? null : request.reviewNote()));
        order.setCancellationReviewedAt(LocalDateTime.now());

        return toOrderResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagerCustomerResponse> getCustomers(String search) {
        List<Order> orders = orderRepository.findAll();

        return userRepository.findAll().stream()
            .filter(this::isCustomer)
            .filter(user -> matchesCustomerSearch(user, search))
            .sorted(Comparator.comparing(User::getCreatedAt).reversed())
            .map(user -> toCustomerResponse(user, orders))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerRevenueReportResponse getRevenueReport(String rawPeriod, String rawFromDate, String rawToDate) {
        String period = normalizePeriod(rawPeriod);
        LocalDate today = LocalDate.now();
        LocalDate fromDate;
        LocalDate toDate;

        if ("custom".equals(period)) {
            fromDate = parseDate(rawFromDate, "from");
            toDate = parseDate(rawToDate, "to");
        } else {
            toDate = rawToDate == null || rawToDate.isBlank() ? today : parseDate(rawToDate, "to");
            fromDate = rawFromDate == null || rawFromDate.isBlank() ? defaultFromDate(period, toDate) : parseDate(rawFromDate, "from");
        }

        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from date must be on or before to date");
        }

        String bucketPeriod = "custom".equals(period) ? resolveCustomBucketPeriod(fromDate, toDate) : period;
        List<Order> orders = orderRepository.findAll();
        List<Order> inScope = orders.stream()
            .filter(this::isCountedOrder)
            .filter(order -> isOnOrAfter(order.getCreatedAt().toLocalDate(), fromDate))
            .filter(order -> !order.getCreatedAt().toLocalDate().isAfter(toDate))
            .toList();

        long totalOrders = inScope.size();
        long successfulOrders = inScope.stream()
            .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
            .count();
        BigDecimal totalRevenue = inScope.stream()
            .filter(this::hasPaidPayment)
            .map(order -> order.getPayment().getAmount())
            .reduce(ZERO, BigDecimal::add);

        List<ManagerRevenuePeriodPointResponse> breakdown = buildRevenueBreakdown(orders, bucketPeriod, fromDate, toDate);
        List<Order> paidOrders = inScope.stream().filter(this::hasPaidPayment).toList();
        List<ManagerTopProductResponse> topProducts = buildTopProducts(paidOrders);
        List<ManagerTopProductResponse> slowProducts = buildSlowProducts(paidOrders);

        return new ManagerRevenueReportResponse(
            bucketPeriod.toUpperCase(Locale.ROOT),
            fromDate.toString(),
            toDate.toString(),
            totalOrders,
            successfulOrders,
            totalRevenue,
            breakdown,
            topProducts,
            slowProducts
        );
    }

    @Override
    @Transactional
    public OrderResponse updateOrderShipment(Long orderId, ManagerShipmentUpdateRequest request) {
        if (request == null || request.shipmentStatus() == null || request.shipmentStatus().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipmentStatus is required");
        }

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot update shipment for cancelled orders");
        }

        Shipment shipment = order.getShipment();
        if (shipment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no shipment information");
        }

        ShipmentStatus nextStatus;
        try {
            nextStatus = ShipmentStatus.valueOf(request.shipmentStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shipment status");
        }

        shipment.setShipmentStatus(nextStatus);
        if (request.trackingNumber() != null) {
            String trackingNumber = request.trackingNumber().trim();
            shipment.setTrackingNumber(trackingNumber.isBlank() ? null : trackingNumber);
        }

        LocalDateTime now = LocalDateTime.now();
        if ((nextStatus == ShipmentStatus.SHIPPED || nextStatus == ShipmentStatus.IN_TRANSIT) && shipment.getShippedAt() == null) {
            shipment.setShippedAt(now);
        }
        if (nextStatus == ShipmentStatus.DELIVERED) {
            if (shipment.getShippedAt() == null) {
                shipment.setShippedAt(now);
            }
            shipment.setDeliveredAt(now);
        }

        syncOrderStatusWithShipment(order, nextStatus);
        return toOrderResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public ProductSummaryDto updateProductStock(Long productId, ManagerProductStockUpdateRequest request) {
        if (request == null || request.stockQuantity() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stockQuantity is required");
        }
        if (request.stockQuantity() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock quantity cannot be negative");
        }

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        product.setStockQuantity(request.stockQuantity());
        return productService.toSummaryDto(productRepository.save(product));
    }

    private boolean isCustomer(User user) {
        return user.getRoles().stream().map(Role::getName).anyMatch(ROLE_CUSTOMER::equals);
    }

    private Order getPendingCancellationOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getStatus() != OrderStatus.CANCELLATION_REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order does not have a pending cancellation request");
        }

        return order;
    }

    private boolean matchesCustomerSearch(User user, String rawSearch) {
        if (isBlank(rawSearch)) {
            return true;
        }
        String search = rawSearch.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(user.getUsername(), search)
            || containsIgnoreCase(user.getEmail(), search)
            || containsIgnoreCase(user.getFirstName(), search)
            || containsIgnoreCase(user.getLastName(), search);
    }

    private boolean matchesPaymentStatus(Payment payment, String rawStatus) {
        if (isBlank(rawStatus)) {
            return true;
        }
        try {
            return payment.getPaymentStatus() == PaymentStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment status filter");
        }
    }

    private boolean matchesPaymentMethod(Payment payment, String rawMethod) {
        if (isBlank(rawMethod)) {
            return true;
        }
        try {
            return payment.getPaymentMethod() == PaymentMethod.valueOf(rawMethod.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment method filter");
        }
    }

    private boolean matchesPaymentSearch(Payment payment, String rawSearch) {
        if (isBlank(rawSearch)) {
            return true;
        }
        String search = rawSearch.trim().toLowerCase(Locale.ROOT);
        Order order = payment.getOrder();
        Shipment shipment = order.getShipment();

        return containsIgnoreCase(order.getOrderNumber(), search)
            || containsIgnoreCase(order.getCustomerEmail(), search)
            || containsIgnoreCase(payment.getTransactionReference(), search)
            || containsIgnoreCase(payment.getProviderTxnId(), search)
            || containsIgnoreCase(shipment == null ? null : shipment.getRecipientName(), search)
            || containsIgnoreCase(shipment == null ? null : shipment.getPhoneNumber(), search);
    }

    private BigDecimal sumPaidOrdersWithin(List<Order> orders, LocalDate fromDate, LocalDate toDate) {
        return orders.stream()
            .map(Order::getPayment)
            .filter(payment -> payment != null && payment.getPaymentStatus() == PaymentStatus.PAID)
            .filter(payment -> isWithinRange(payment.getPaidAt(), fromDate, toDate))
            .map(Payment::getAmount)
            .reduce(ZERO, BigDecimal::add);
    }

    private boolean isWithinRange(LocalDateTime timestamp, LocalDate fromDate, LocalDate toDate) {
        if (timestamp == null) {
            return false;
        }
        LocalDate date = timestamp.toLocalDate();
        return !date.isBefore(fromDate) && !date.isAfter(toDate);
    }

    private ManagerCustomerResponse toCustomerResponse(User user, List<Order> orders) {
        List<Order> customerOrders = orders.stream()
            .filter(order -> order.getUser() != null)
            .filter(order -> user.getId().equals(order.getUser().getId()))
            .toList();

        BigDecimal totalSpent = customerOrders.stream()
            .map(Order::getPayment)
            .filter(payment -> payment != null && payment.getPaymentStatus() == PaymentStatus.PAID)
            .map(Payment::getAmount)
            .reduce(ZERO, BigDecimal::add);

        return new ManagerCustomerResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getActive(),
            user.getVerified(),
            customerOrders.size(),
            totalSpent,
            user.getCreatedAt()
        );
    }

    private ManagerPaymentResponse toPaymentResponse(Payment payment) {
        Order order = payment.getOrder();
        Shipment shipment = order.getShipment();

        return new ManagerPaymentResponse(
            payment.getId(),
            order.getId(),
            order.getOrderNumber(),
            shipment == null ? null : shipment.getRecipientName(),
            order.getCustomerEmail(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getPaymentMethod().name(),
            payment.getPaymentStatus().name(),
            payment.getTransactionReference(),
            payment.getProviderTxnId(),
            payment.getProviderResponse(),
            payment.getPaidAt(),
            order.getCreatedAt()
        );
    }

    private OrderResponse toOrderResponse(Order order) {
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
                .map(item -> new com.ebike.orderModule.dto.response.OrderItemResponse(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getLineTotal()
                ))
                .toList(),
            shipment == null ? null : new com.ebike.orderModule.dto.response.ShipmentResponse(
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
                shipment.getPickupShowroom() == null ? null : new com.ebike.orderModule.dto.response.ShowroomResponse(
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

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void syncOrderStatusWithShipment(Order order, ShipmentStatus shipmentStatus) {
        switch (shipmentStatus) {
            case PREPARING -> {
                if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED) {
                    order.setStatus(OrderStatus.PROCESSING);
                }
            }
            case SHIPPED, IN_TRANSIT -> order.setStatus(OrderStatus.SHIPPED);
            case DELIVERED -> order.setStatus(OrderStatus.DELIVERED);
            default -> {
            }
        }
    }

    private String normalizeOptional(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String normalizePeriod(String rawPeriod) {
        String period = rawPeriod == null ? "day" : rawPeriod.trim().toLowerCase(Locale.ROOT);
        if (!List.of("day", "month", "quarter", "year", "custom").contains(period)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must be day, month, quarter, year, or custom");
        }
        return period;
    }

    private LocalDate parseDate(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " date is required");
        }
        try {
            return LocalDate.parse(rawValue.trim());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + " date format, expected yyyy-MM-dd");
        }
    }

    private LocalDate defaultFromDate(String period, LocalDate toDate) {
        return switch (period) {
            case "month" -> toDate.minusMonths(11).withDayOfMonth(1);
            case "quarter" -> toDate.minusMonths(21).withDayOfMonth(1);
            case "year" -> toDate.minusYears(4).withDayOfYear(1);
            default -> toDate.minusDays(29);
        };
    }

    private String resolveCustomBucketPeriod(LocalDate fromDate, LocalDate toDate) {
        long days = toDate.toEpochDay() - fromDate.toEpochDay() + 1;
        if (days <= 62) {
            return "day";
        }
        if (days <= 730) {
            return "month";
        }
        return "year";
    }

    private boolean isCountedOrder(Order order) {
        return order.getStatus() != OrderStatus.CANCELLED;
    }

    private boolean hasPaidPayment(Order order) {
        Payment payment = order.getPayment();
        return payment != null && payment.getPaymentStatus() == PaymentStatus.PAID;
    }

    private boolean isOnOrAfter(LocalDate value, LocalDate fromDate) {
        return !value.isBefore(fromDate);
    }

    private List<ManagerRevenuePeriodPointResponse> buildRevenueBreakdown(
        List<Order> orders,
        String period,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        List<BucketRange> buckets = buildBucketRanges(period, fromDate, toDate);
        return buckets.stream()
            .map(bucket -> {
                long orderCount = orders.stream()
                    .filter(this::isCountedOrder)
                    .filter(order -> {
                        LocalDate createdAt = order.getCreatedAt().toLocalDate();
                        return !createdAt.isBefore(bucket.start()) && !createdAt.isAfter(bucket.end());
                    })
                    .count();

                BigDecimal revenue = orders.stream()
                    .filter(this::isCountedOrder)
                    .filter(this::hasPaidPayment)
                    .filter(order -> {
                        LocalDate createdAt = order.getCreatedAt().toLocalDate();
                        return !createdAt.isBefore(bucket.start()) && !createdAt.isAfter(bucket.end());
                    })
                    .map(order -> order.getPayment().getAmount())
                    .reduce(ZERO, BigDecimal::add);

                return new ManagerRevenuePeriodPointResponse(bucket.label(), orderCount, revenue);
            })
            .toList();
    }

    private List<BucketRange> buildBucketRanges(String period, LocalDate fromDate, LocalDate toDate) {
        List<BucketRange> buckets = new ArrayList<>();

        switch (period) {
            case "month" -> {
                YearMonth cursor = YearMonth.from(fromDate);
                YearMonth end = YearMonth.from(toDate);
                while (!cursor.isAfter(end)) {
                    LocalDate start = cursor.atDay(1);
                    LocalDate endOfMonth = cursor.atEndOfMonth();
                    if (start.isBefore(fromDate)) {
                        start = fromDate;
                    }
                    if (endOfMonth.isAfter(toDate)) {
                        endOfMonth = toDate;
                    }
                    buckets.add(new BucketRange(cursor.toString(), start, endOfMonth));
                    cursor = cursor.plusMonths(1);
                }
            }
            case "quarter" -> {
                LocalDate cursor = quarterStart(fromDate);
                while (!cursor.isAfter(toDate)) {
                    LocalDate quarterEnd = quarterEnd(cursor);
                    LocalDate bucketStart = cursor.isBefore(fromDate) ? fromDate : cursor;
                    LocalDate bucketEnd = quarterEnd.isAfter(toDate) ? toDate : quarterEnd;
                    buckets.add(new BucketRange(quarterLabel(cursor), bucketStart, bucketEnd));
                    cursor = quarterStart(quarterEnd.plusDays(1));
                }
            }
            case "year" -> {
                int year = fromDate.getYear();
                int endYear = toDate.getYear();
                while (year <= endYear) {
                    LocalDate start = LocalDate.of(year, 1, 1);
                    LocalDate end = LocalDate.of(year, 12, 31);
                    if (start.isBefore(fromDate)) {
                        start = fromDate;
                    }
                    if (end.isAfter(toDate)) {
                        end = toDate;
                    }
                    buckets.add(new BucketRange(String.valueOf(year), start, end));
                    year += 1;
                }
            }
            default -> {
                LocalDate cursor = fromDate;
                while (!cursor.isAfter(toDate)) {
                    buckets.add(new BucketRange(cursor.toString(), cursor, cursor));
                    cursor = cursor.plusDays(1);
                }
            }
        }

        return buckets;
    }

    private LocalDate quarterStart(LocalDate date) {
        int quarter = (date.getMonthValue() - 1) / 3;
        return LocalDate.of(date.getYear(), quarter * 3 + 1, 1);
    }

    private LocalDate quarterEnd(LocalDate quarterStartDate) {
        return quarterStartDate.plusMonths(3).minusDays(1);
    }

    private String quarterLabel(LocalDate quarterStartDate) {
        int quarter = (quarterStartDate.getMonthValue() - 1) / 3 + 1;
        return quarterStartDate.getYear() + "-Q" + quarter;
    }

    private List<ManagerTopProductResponse> buildTopProducts(List<Order> paidOrders) {
        Map<Long, TopProductAccumulator> aggregates = new HashMap<>();

        for (Order order : paidOrders) {
            for (OrderItem item : order.getItems()) {
                Long productId = item.getProduct().getId();
                TopProductAccumulator accumulator = aggregates.computeIfAbsent(
                    productId,
                    ignored -> new TopProductAccumulator(productId, item.getProductName())
                );
                accumulator.quantitySold += item.getQuantity();
                accumulator.revenue = accumulator.revenue.add(
                    item.getLineTotal() == null ? ZERO : item.getLineTotal()
                );
            }
        }

        return aggregates.values().stream()
            .sorted(Comparator.comparing((TopProductAccumulator accumulator) -> accumulator.revenue).reversed())
            .limit(10)
            .map(accumulator -> new ManagerTopProductResponse(
                accumulator.productId,
                accumulator.productName,
                accumulator.quantitySold,
                accumulator.revenue
            ))
            .toList();
    }

    private List<ManagerTopProductResponse> buildSlowProducts(List<Order> paidOrders) {
        Map<Long, TopProductAccumulator> aggregates = new HashMap<>();

        for (Product product : productRepository.findAll()) {
            if (Boolean.FALSE.equals(product.getActive())) {
                continue;
            }
            aggregates.put(product.getId(), new TopProductAccumulator(product.getId(), product.getName()));
        }

        for (Order order : paidOrders) {
            for (OrderItem item : order.getItems()) {
                Long productId = item.getProduct().getId();
                TopProductAccumulator accumulator = aggregates.computeIfAbsent(
                    productId,
                    ignored -> new TopProductAccumulator(productId, item.getProductName())
                );
                accumulator.quantitySold += item.getQuantity();
                accumulator.revenue = accumulator.revenue.add(
                    item.getLineTotal() == null ? ZERO : item.getLineTotal()
                );
            }
        }

        return aggregates.values().stream()
            .sorted(
                Comparator
                    .comparing((TopProductAccumulator accumulator) -> accumulator.quantitySold)
                    .thenComparing(accumulator -> accumulator.revenue)
                    .thenComparing(accumulator -> accumulator.productName, String.CASE_INSENSITIVE_ORDER)
            )
            .limit(10)
            .map(accumulator -> new ManagerTopProductResponse(
                accumulator.productId,
                accumulator.productName,
                accumulator.quantitySold,
                accumulator.revenue
            ))
            .toList();
    }

    private record BucketRange(String label, LocalDate start, LocalDate end) {
    }

    private static final class TopProductAccumulator {
        private final Long productId;
        private final String productName;
        private long quantitySold;
        private BigDecimal revenue = ZERO;

        private TopProductAccumulator(Long productId, String productName) {
            this.productId = productId;
            this.productName = productName;
        }
    }
}
