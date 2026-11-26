package com.ebike.qa.unit.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.admin.PricingService;
import com.ebike.service.installment.InstallmentPlanner;
import com.ebike.service.order.OrderEmailVerificationService;
import com.ebike.service.order.impl.OrderServiceImpl;
import com.ebike.service.stock.StockAlertService;
import com.ebike.service.stock.StockLedger;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Mocks for every collaborator of OrderServiceImpl plus the defaults a normal shop would have. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class OrderServiceTestBase {

    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock ProductRepository productRepository;
    @Mock ShowroomRepository showroomRepository;
    @Mock PromotionRepository promotionRepository;
    @Mock OrderEmailVerificationService emailVerification;
    @Mock PricingService pricingService;
    @Mock AuditLogService auditLog;
    @Mock StockAlertService stockAlerts;
    @Mock InstallmentPlanner installmentPlanner;
    @Mock InstallmentApplicationRepository installments;
    @Mock StockLedger stockLedger;

    OrderServiceImpl service;

    @BeforeEach
    void buildService() {
        service = new OrderServiceImpl(orderRepository, userRepository, productRepository, showroomRepository, promotionRepository,
            emailVerification, pricingService, auditLog, stockAlerts, installmentPlanner, installments, stockLedger);
        when(pricingService.registrationServicePerBike()).thenReturn(new BigDecimal("500000"));
        when(pricingService.registrationServiceOffered()).thenReturn(true);
        when(pricingService.registrationTaxRateOther()).thenReturn(new BigDecimal("2"));
        when(pricingService.registrationTaxRateCentral()).thenReturn(new BigDecimal("5"));
        when(pricingService.homeDeliveryPerBike(true)).thenReturn(new BigDecimal("200000"));
        when(pricingService.homeDeliveryPerBike(false)).thenReturn(new BigDecimal("800000"));
        when(promotionRepository.tryIncrementUsage(anyLong())).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(1000L);
            }
            long next = 1;
            for (OrderItem item : order.getItems()) {
                if (item.getId() == null) {
                    item.setId(next);
                }
                next++;
            }
            return order;
        });
        when(orderRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** A valid store-pickup request for one bike; tests change one field at a time with {@link #copy}. */
    static OrderCreateRequest pickupRequest(Long productId, int quantity) {
        return new OrderCreateRequest(null, "Nguyen Van A", "0901234567", "Khach@Example.com", null, 1L, null,
            "PAY_LATER", null, null, null, false, null, List.of(new OrderCreateItemRequest(productId, quantity, null)),
            "STORE_PICKUP", null);
    }

    static OrderCreateRequest homeRequest(Long productId, int quantity, String province) {
        return new OrderCreateRequest(null, "Nguyen Van A", "0901234567", "khach@example.com", null, null,
            "12 Nguyen Hue, Ben Nghe", "PAY_LATER", null, null, null, false, null,
            List.of(new OrderCreateItemRequest(productId, quantity, null)), "HOME_DELIVERY", province);
    }

    static OrderCreateRequest copy(OrderCreateRequest request, Consumer<Fields> change) {
        Fields fields = new Fields(request);
        change.accept(fields);
        return fields.build();
    }

    /** A mutable copy of an OrderCreateRequest (the record has no withers). */
    static final class Fields {
        String customerName;
        String phoneNumber;
        String customerEmail;
        UUID verificationId;
        Long showroomId;
        String address;
        String paymentMethod;
        String discountCode;
        BigDecimal shippingFee;
        BigDecimal discountAmount;
        Boolean includeRegistration;
        String notes;
        List<OrderCreateItemRequest> items;
        String deliveryMethod;
        String province;

        Fields(OrderCreateRequest r) {
            customerName = r.customerName();
            phoneNumber = r.phoneNumber();
            customerEmail = r.customerEmail();
            verificationId = r.emailVerificationSessionId();
            showroomId = r.pickupShowroomId();
            address = r.detailedAddress();
            paymentMethod = r.paymentMethod();
            discountCode = r.discountCode();
            shippingFee = r.shippingFee();
            discountAmount = r.discountAmount();
            includeRegistration = r.includeRegistrationService();
            notes = r.notes();
            items = r.items();
            deliveryMethod = r.deliveryMethod();
            province = r.province();
        }

        OrderCreateRequest build() {
            return new OrderCreateRequest(null, customerName, phoneNumber, customerEmail, verificationId, showroomId, address,
                paymentMethod, discountCode, shippingFee, discountAmount, includeRegistration, notes, items, deliveryMethod, province);
        }
    }
}
