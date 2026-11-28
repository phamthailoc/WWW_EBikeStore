package com.ebike.service.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.manager.request.ManagerProductStockUpdateRequest;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.PaymentRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.manager.impl.ManagerServiceImpl;
import com.ebike.service.product.ProductService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V17: a product with colours sells from its colours, so its stock is the sum of the colours' stock. */
@ExtendWith(MockitoExtension.class)
class ManagerStockTest {
    @Mock OrderRepository orders;
    @Mock com.ebike.service.stock.StockLedger ledger;
    @Mock PaymentRepository payments;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock ProductService productService;
    @Mock com.ebike.service.stock.StockAlertService stockAlerts;
    ManagerServiceImpl service;
    Product product;
    ProductVariant white;
    ProductVariant black;
    ProductVariant retired;

    @BeforeEach void setup() {
        service = new ManagerServiceImpl(orders, payments, users, products, productService, org.mockito.Mockito.mock(com.ebike.service.admin.AuditLogService.class), stockAlerts, org.mockito.Mockito.mock(com.ebike.repository.installment.InstallmentApplicationRepository.class), java.time.Duration.ofHours(72), ledger, org.mockito.Mockito.mock(com.ebike.service.order.OrderService.class));
        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        white = variant(11L, 4, true);
        black = variant(12L, 6, true);
        retired = variant(13L, 9, false);
        product.getVariants().addAll(java.util.List.of(white, black, retired));
        product.setStockQuantity(10);
        lenient().when(products.findById(1L)).thenReturn(Optional.of(product));
        lenient().when(products.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProductVariant variant(long id, int stock, boolean active) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setStockQuantity(stock);
        variant.setActive(active);
        variant.setProduct(product);
        return variant;
    }

    private ManagerProductStockUpdateRequest stock(Integer quantity) { return new ManagerProductStockUpdateRequest(quantity, null); }

    @Test void restockingAColourUpdatesThatColourAndTheProductTotal() {
        service.updateVariantStock(1L, 11L, stock(10));
        assertEquals(10, white.getStockQuantity());
        assertEquals(6, black.getStockQuantity());
        // 10 + 6; the retired colour is not for sale and does not count
        assertEquals(16, product.getStockQuantity());
    }

    @Test void aSoldOutColourCanBeBroughtBack() {
        white.setStockQuantity(0);
        product.setStockQuantity(6);
        service.updateVariantStock(1L, 11L, stock(3));
        assertEquals(3, white.getStockQuantity());
        assertEquals(9, product.getStockQuantity());
    }

    @Test void aColourGoingFromSoldOutToInStockTellsTheCustomersWaiting() {
        white.setStockQuantity(0);
        service.updateVariantStock(1L, 11L, stock(3));
        verify(stockAlerts).notifyRestocked(white);
    }

    @Test void toppingUpAColourAlreadyInStockTellsNobody() {
        service.updateVariantStock(1L, 11L, stock(10));
        verify(stockAlerts, never()).notifyRestocked(any());
    }

    @Test void settingASoldOutColourToZeroAgainTellsNobody() {
        white.setStockQuantity(0);
        service.updateVariantStock(1L, 11L, stock(0));
        verify(stockAlerts, never()).notifyRestocked(any());
    }

    @Test void aRetiredColourBroughtBackTellsNobody() {
        retired.setStockQuantity(0);
        service.updateVariantStock(1L, 13L, stock(4));
        verify(stockAlerts, never()).notifyRestocked(any());
    }

    @Test void aColourOfAnotherProductIsNotFound() {
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 99L, stock(1)));
        verify(products, never()).save(any());
    }

    @Test void negativeOrMissingQuantitiesAreRefused() {
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 11L, stock(-1)));
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 11L, stock(null)));
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 11L, null));
        assertEquals(4, white.getStockQuantity());
    }

    @Test void theProductTotalCannotBeSetDirectlyWhenItHasColours() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.updateProductStock(1L, stock(50)));
        assertEquals(400, ex.getStatusCode().value());
        assertEquals(10, product.getStockQuantity());
    }

    @Test void aColourEditIsLockedFirstThenWrittenAsAnAdjustmentWithTheDifferenceAndTheNote() {
        com.ebike.model.entity.auth.User staffMember = new com.ebike.model.entity.auth.User();
        when(ledger.currentStaff()).thenReturn(staffMember);

        service.updateVariantStock(1L, 11L, new ManagerProductStockUpdateRequest(10, "  Kiểm kê   tháng 11 "));

        var inOrder = org.mockito.Mockito.inOrder(ledger);
        inOrder.verify(ledger).lock(any(), any());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.List<com.ebike.service.stock.StockLedger.Entry>> entries = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        inOrder.verify(ledger).record(entries.capture(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(staffMember));
        var line = entries.getValue().get(0);
        assertEquals(6, line.change());
        assertEquals(10, line.balanceAfter());
        assertEquals("Kiểm kê tháng 11", line.note());
        assertEquals(com.ebike.model.entity.stock.StockMovementReason.ADJUSTMENT, line.reason());
        assertEquals(white, line.variant());
    }

    @Test void savingTheSameFigureAgainWritesNothingOnTheLedger() {
        service.updateVariantStock(1L, 11L, stock(4));
        verify(ledger, never()).record(any(), any(), any(), any(), any());
    }

    @Test void loweringAColourIsAnAdjustmentBelowZero() {
        service.updateVariantStock(1L, 11L, stock(1));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.List<com.ebike.service.stock.StockLedger.Entry>> entries = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(ledger).record(entries.capture(), any(), any(), any(), any());
        assertEquals(-3, entries.getValue().get(0).change());
        assertEquals(1, entries.getValue().get(0).balanceAfter());
    }

    @Test void aTooLongNoteOrAnAbsurdFigureIsRefusedBeforeAnyStockIsTouched() {
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 11L, new ManagerProductStockUpdateRequest(5, "x".repeat(501))));
        assertThrows(ResponseStatusException.class, () -> service.updateVariantStock(1L, 11L, stock(100_001)));
        assertEquals(4, white.getStockQuantity());
        verify(ledger, never()).lock(any(), any());
    }

    @Test void aBikeWithoutColoursIsAdjustedAtBikeLevel() {
        product.getVariants().clear();
        service.updateProductStock(1L, new ManagerProductStockUpdateRequest(25, null));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.List<com.ebike.service.stock.StockLedger.Entry>> entries = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(ledger).record(entries.capture(), any(), any(), any(), any());
        assertEquals(15, entries.getValue().get(0).change());
        assertEquals(25, entries.getValue().get(0).balanceAfter());
        assertEquals(null, entries.getValue().get(0).variant());
    }

    @Test void aProductWithoutColoursStillTakesItsTotalDirectly() {
        product.getVariants().clear();
        service.updateProductStock(1L, stock(50));
        assertEquals(50, product.getStockQuantity());
    }
}
