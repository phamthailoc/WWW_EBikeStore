package com.ebike.service.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ebike.model.entity.admin.Promotion;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.order.impl.OrderServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** Staff change the quantity of one line of a pending, pay-on-hand-over order: stock, fees, discount and totals follow. */
@ExtendWith(MockitoExtension.class)
class OrderItemQuantityTest {
    @Mock OrderRepository orders;
    @Mock com.ebike.service.stock.StockLedger ledger;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock ShowroomRepository showrooms;
    @Mock PromotionRepository promotions;
    @Mock OrderEmailVerificationService verification;
    @Mock com.ebike.repository.admin.PricingRuleRepository pricingRules;
    @Mock com.ebike.service.stock.StockAlertService stockAlerts;
    OrderServiceImpl service;
    Order order;
    OrderItem item;
    Product product;
    Payment payment;

    @BeforeEach void setup() {
        service = new OrderServiceImpl(orders, users, products, showrooms, promotions, verification, new com.ebike.service.admin.PricingService(pricingRules),
            org.mockito.Mockito.mock(com.ebike.service.admin.AuditLogService.class), stockAlerts,
            org.mockito.Mockito.mock(com.ebike.service.installment.InstallmentPlanner.class),
            org.mockito.Mockito.mock(com.ebike.repository.installment.InstallmentApplicationRepository.class), ledger);
        product = new Product();
        product.setId(1L);
        product.setActive(true);
        product.setStockQuantity(10);
        item = new OrderItem();
        item.setId(100L);
        item.setProduct(product);
        item.setProductName("Xe thử");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("10000000"));
        item.setLineTotal(new BigDecimal("20000000"));
        payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.PAY_LATER);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-T");
        order.setStatus(OrderStatus.PENDING);
        order.setPayment(payment);
        order.getItems().add(item);
        order.setSubtotal(new BigDecimal("20000000"));
        order.setShippingFee(new BigDecimal("1000000"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("21000000"));
        lenient().when(orders.findLockedById(10L)).thenReturn(Optional.of(order));
        lenient().when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void raisingTheQuantityTakesStockAndScalesTheFeesAndTotal() {
        service.updateItemQuantity(10L, 100L, 3, null);
        assertEquals(9, product.getStockQuantity());
        assertEquals(3, item.getQuantity());
        assertEquals(0, new BigDecimal("30000000").compareTo(order.getSubtotal()));
        assertEquals(0, new BigDecimal("1500000").compareTo(order.getShippingFee()));
        assertEquals(0, new BigDecimal("31500000").compareTo(order.getTotalAmount()));
        assertEquals(0, order.getTotalAmount().compareTo(payment.getAmount()));
    }

    @Test void theCustomerWithAnAccountIsToldAboutTheChange() {
        var publisher = org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class);
        new com.ebike.listener.notification.NotificationEventBridge().setApplicationEventPublisher(publisher);
        try {
            com.ebike.model.entity.auth.User customer = new com.ebike.model.entity.auth.User();
            customer.setId(7L);
            order.setUser(customer);
            service.updateItemQuantity(10L, 100L, 3, null);
            verify(publisher).publishEvent(org.mockito.ArgumentMatchers.argThat((Object e) -> e instanceof com.ebike.event.notification.OrderItemQuantityChangedEvent ev
                && ev.userId() == 7L && ev.quantityBefore() == 2 && ev.quantityAfter() == 3));
            order.setUser(null);
            org.mockito.Mockito.clearInvocations(publisher);
            service.updateItemQuantity(10L, 100L, 2, null);
            verify(publisher, never()).publishEvent(any(Object.class));
        } finally {
            new com.ebike.listener.notification.NotificationEventBridge().setApplicationEventPublisher(null);
        }
    }

    @Test void feesStayInWholeDong() {
        item.setQuantity(3);
        item.setLineTotal(new BigDecimal("30000000"));
        order.setSubtotal(new BigDecimal("30000000"));
        order.setShippingFee(new BigDecimal("1000000"));
        service.updateItemQuantity(10L, 100L, 2, null);
        assertEquals(0, new BigDecimal("666666").compareTo(order.getShippingFee()));
        // the rate (333,333) is rounded once; going back and forth never drifts after that
        service.updateItemQuantity(10L, 100L, 3, null);
        assertEquals(0, new BigDecimal("999999").compareTo(order.getShippingFee()));
        service.updateItemQuantity(10L, 100L, 2, null);
        assertEquals(0, new BigDecimal("666666").compareTo(order.getShippingFee()));
    }

    @Test void loweringTheQuantityGivesStockBack() {
        service.updateItemQuantity(10L, 100L, 1, null);
        assertEquals(11, product.getStockQuantity());
        assertEquals(0, new BigDecimal("10000000").compareTo(order.getSubtotal()));
        assertEquals(0, new BigDecimal("500000").compareTo(order.getShippingFee()));
    }

    @Test void aPercentageDiscountKeepsItsShareOfTheSubtotal() {
        Promotion promotion = new Promotion();
        promotion.setDiscountType("PERCENTAGE");
        promotion.setDiscountValue(new BigDecimal("10"));
        order.setPromotion(promotion);
        order.setDiscountAmount(new BigDecimal("2000000"));
        // today's promotion is worth more than it was when the order was placed: the edit must not pick that up
        promotion.setDiscountValue(new BigDecimal("50"));
        service.updateItemQuantity(10L, 100L, 4, null);
        assertEquals(0, new BigDecimal("4000000").compareTo(order.getDiscountAmount()));
    }

    @Test void aCappedPercentageDiscountStaysUnderItsCap() {
        Promotion promotion = new Promotion();
        promotion.setDiscountType("PERCENTAGE");
        promotion.setDiscountValue(new BigDecimal("10"));
        promotion.setMaxDiscountAmount(new BigDecimal("2000000"));
        order.setPromotion(promotion);
        order.setDiscountAmount(new BigDecimal("2000000"));
        service.updateItemQuantity(10L, 100L, 6, null);
        assertEquals(0, new BigDecimal("2000000").compareTo(order.getDiscountAmount()));
    }

    @Test void aFixedDiscountStaysAndANullPromotionIsTreatedAsFixed() {
        Promotion promotion = new Promotion();
        promotion.setDiscountType("FIXED");
        promotion.setDiscountValue(new BigDecimal("500000"));
        order.setPromotion(promotion);
        order.setDiscountAmount(new BigDecimal("500000"));
        service.updateItemQuantity(10L, 100L, 4, null);
        assertEquals(0, new BigDecimal("500000").compareTo(order.getDiscountAmount()));
        order.setPromotion(null);
        service.updateItemQuantity(10L, 100L, 1, null);
        assertEquals(0, new BigDecimal("500000").compareTo(order.getDiscountAmount()));
    }

    @Test void theLedgerSaysSaleWhenRaisedAndRestoreWhenLowered() {
        service.updateItemQuantity(10L, 100L, 3, null);
        verify(ledger).record(org.mockito.ArgumentMatchers.argThat(entries -> entries.get(0).change() == -1
            && entries.get(0).reason() == com.ebike.model.entity.stock.StockMovementReason.SALE), any(), any(), any(), any());
        service.updateItemQuantity(10L, 100L, 1, null);
        verify(ledger).record(org.mockito.ArgumentMatchers.argThat(entries -> entries.get(0).change() == 2
            && entries.get(0).reason() == com.ebike.model.entity.stock.StockMovementReason.CANCEL_RESTORE), any(), any(), any(), any());
    }

    @Test void moreThanTheStockOnHandIsRefused() {
        product.setStockQuantity(0);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.updateItemQuantity(10L, 100L, 3, null)).getStatusCode().value());
        assertEquals(2, item.getQuantity());
    }

    @Test void aBikeNoLongerForSaleCannotBeIncreased() {
        product.setActive(false);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.updateItemQuantity(10L, 100L, 3, null)).getStatusCode().value());
    }

    @Test void onlyAPendingPayLaterOrderCanBeEdited() {
        order.setStatus(OrderStatus.CONFIRMED);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.updateItemQuantity(10L, 100L, 3, null)).getStatusCode().value());
        order.setStatus(OrderStatus.PENDING);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.updateItemQuantity(10L, 100L, 3, null)).getStatusCode().value());
        verify(orders, never()).save(any());
    }

    @Test void quantityOutsideOneToTenIsRefused() {
        for (Integer bad : new Integer[] {null, 0, 11}) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.updateItemQuantity(10L, 100L, bad, null)).getStatusCode().value());
        }
    }

    @Test void anUnknownLineIsNotFound() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.updateItemQuantity(10L, 999L, 3, null)).getStatusCode().value());
    }
}
