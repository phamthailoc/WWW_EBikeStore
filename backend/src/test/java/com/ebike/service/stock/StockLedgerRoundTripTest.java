package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.dto.order.request.OrderCreateRequest;
import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.stock.StockMovement;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.stock.StockMovementRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.admin.PricingService;
import com.ebike.service.installment.InstallmentPlanner;
import com.ebike.service.order.OrderEmailVerificationService;
import com.ebike.service.order.impl.OrderServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

/** V26: order a colour, then cancel the order: the ledger has two lines that cancel each other out and always add up to the stock. */
@ExtendWith(MockitoExtension.class)
class StockLedgerRoundTripTest {
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock ShowroomRepository showrooms;
    @Mock PromotionRepository promotions;
    @Mock OrderEmailVerificationService verification;
    @Mock PricingRuleRepository pricingRules;
    @Mock StockMovementRepository movements;
    @Mock EntityManager entityManager;
    @Mock Authentication staff;
    OrderServiceImpl service;
    Product product;
    ProductVariant white;
    final List<StockMovement> written = new ArrayList<>();

    @BeforeEach void setup() {
        StockLedger ledger = new StockLedger(movements, users);
        ReflectionTestUtils.setField(ledger, "entityManager", entityManager);
        lenient().when(entityManager.contains(any())).thenReturn(true);
        lenient().when(entityManager.getLockMode(any())).thenReturn(LockModeType.NONE);
        lenient().when(movements.saveAll(any())).thenAnswer(invocation -> {
            Iterable<StockMovement> lines = invocation.getArgument(0);
            lines.forEach(written::add);
            return List.copyOf(written);
        });
        service = new OrderServiceImpl(orders, users, products, showrooms, promotions, verification, new PricingService(pricingRules),
            mock(AuditLogService.class), mock(StockAlertService.class), mock(InstallmentPlanner.class),
            mock(com.ebike.repository.installment.InstallmentApplicationRepository.class), ledger);

        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setName("DIBAO Test");
        product.setPrice(new BigDecimal("20300000"));
        product.setActive(true);
        ProductSpecification spec = new ProductSpecification();
        spec.setVehicleType(VehicleType.E_SCOOTER);
        spec.setMaxSpeedKmh(new BigDecimal("20"));
        spec.setMotorPowerWatts(200);
        product.setSpecification(spec);
        white = new ProductVariant();
        ReflectionTestUtils.setField(white, "id", 11L);
        white.setColorName("Trắng");
        white.setActive(true);
        white.setStockQuantity(8);
        white.setProduct(product);
        product.getVariants().add(white);
        product.setStockQuantity(8);
        when(products.findById(1L)).thenReturn(Optional.of(product));

        Showroom showroom = new Showroom();
        showroom.setId(9L);
        showroom.setName("KINETIC Test");
        showroom.setCity("TP. Ho Chi Minh");
        showroom.setDistrict("Quan 1");
        showroom.setAddress("1 Test");
        showroom.setActive(true);
        lenient().when(showrooms.findByActiveTrueOrderByDistrictAscNameAsc()).thenReturn(List.of(showroom));
    }

    @Test void anOrderAndItsCancellationLeaveTwoLinesThatCancelOutAndAddUpToTheStock() {
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        when(orders.save(saved.capture())).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 10L);
            return order;
        });
        OrderCreateRequest request = new OrderCreateRequest(null, "Khách Hàng", "0901234567", "checkout@example.com", null,
            null, "123 Nguyễn Trãi, Phường Bến Thành", "PAY_LATER", null, BigDecimal.ZERO, null, false, null,
            List.of(new OrderCreateItemRequest(1L, 3, 11L)), "HOME_DELIVERY", "Hà Nội");
        service.createOrder(request, null);
        Order order = saved.getValue();

        // 8 in stock, 3 sold
        assertEquals(5, white.getStockQuantity());
        assertEquals(1, written.size());
        assertEquals(StockMovementReason.SALE, written.get(0).getReason());
        assertEquals(-3, written.get(0).getQuantityChange());
        assertEquals(5, written.get(0).getBalanceAfter());
        assertEquals(10L, written.get(0).getOrderId());
        assertEquals(order.getOrderNumber(), written.get(0).getOrderNumber());
        assertEquals(white, written.get(0).getVariant());

        User manager = new User();
        manager.setId(99L);
        when(staff.getName()).thenReturn("manager");
        when(users.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(orders.findLockedById(10L)).thenReturn(Optional.of(order));
        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "Hết xe"), staff);

        assertEquals(8, white.getStockQuantity());
        assertEquals(2, written.size());
        assertEquals(StockMovementReason.CANCEL_RESTORE, written.get(1).getReason());
        assertEquals(3, written.get(1).getQuantityChange());
        assertEquals(8, written.get(1).getBalanceAfter());
        assertEquals(10L, written.get(1).getOrderId());
        assertEquals(manager, written.get(1).getActor());
        // the two lines cancel out, and with the 8 that were on the shelves at the start they add up to what the colour has now
        assertEquals(0, written.stream().mapToInt(StockMovement::getQuantityChange).sum());
        assertEquals(8, 8 + written.stream().mapToInt(StockMovement::getQuantityChange).sum());
        assertEquals(white.getStockQuantity(), written.get(1).getBalanceAfter());
        assertEquals(8, product.getStockQuantity());
    }
}
