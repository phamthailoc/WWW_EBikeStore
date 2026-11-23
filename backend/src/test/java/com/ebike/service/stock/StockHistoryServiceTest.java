package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.stock.StockMovement;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.model.entity.stock.StockReceipt;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.stock.StockMovementRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V26: the history of a bike's stock: where each colour stands and the lines of the ledger, newest first. */
@ExtendWith(MockitoExtension.class)
class StockHistoryServiceTest {
    @Mock ProductRepository products;
    @Mock StockMovementRepository movements;
    StockHistoryService service;
    Product product;
    ProductVariant white;
    ProductVariant black;

    @BeforeEach void setup() {
        service = new StockHistoryService(products, movements);
        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setName("DIBAO Amy");
        product.setStockQuantity(9);
        white = colour(11L, "Trắng", 5, true, true);
        black = colour(12L, "Đen", 4, false, true);
        product.getVariants().add(black);
        product.getVariants().add(white);
        lenient().when(products.findById(1L)).thenReturn(Optional.of(product));
    }

    private ProductVariant colour(long id, String name, int stock, boolean isDefault, boolean active) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setColorName(name);
        variant.setStockQuantity(stock);
        variant.setDefaultVariant(isDefault);
        variant.setActive(active);
        variant.setProduct(product);
        return variant;
    }

    private StockMovement line(long id, ProductVariant variant, StockMovementReason reason, int change, int balance) {
        StockMovement line = new StockMovement();
        ReflectionTestUtils.setField(line, "id", id);
        line.setProduct(product);
        line.setVariant(variant);
        line.setReason(reason);
        line.setQuantityChange(change);
        line.setBalanceAfter(balance);
        return line;
    }

    @Test void balancesShowWhereEachColourStandsAndWhetherTheLedgerAgrees() {
        when(movements.sumOfVariant(11L)).thenReturn(5L);
        when(movements.sumOfVariant(12L)).thenReturn(7L);
        when(movements.findOfProduct(eq(1L), any(), eq(Long.MAX_VALUE), any())).thenReturn(List.of());

        var history = service.history(1L, null, null, null, null);

        assertEquals(2, history.balances().size());
        // the default colour first
        assertEquals("Trắng", history.balances().get(0).colorName());
        assertTrue(history.balances().get(0).matches());
        assertEquals("Đen", history.balances().get(1).colorName());
        assertFalse(history.balances().get(1).matches());
        assertEquals(7, history.balances().get(1).ledgerTotal());
        assertEquals(9, history.stockQuantity());
    }

    @Test void aBikeWithoutActiveColoursIsOneBikeLevelBalance() {
        white.setActive(false);
        black.setActive(false);
        product.setStockQuantity(6);
        when(movements.sumOfVariant(any())).thenReturn(0L);
        when(movements.sumOfProductLevel(1L)).thenReturn(6L);
        when(movements.findOfProduct(any(), any(), any(), any())).thenReturn(List.of());

        var history = service.history(1L, null, null, null, null);

        var bikeLevel = history.balances().get(history.balances().size() - 1);
        assertNull(bikeLevel.variantId());
        assertEquals(6, bikeLevel.stock());
        assertTrue(bikeLevel.matches());
    }

    @Test void theLinesAreMappedWithTheOrderTheReceiptAndTheActor() {
        User manager = new User();
        manager.setFirstName("An");
        manager.setLastName("Nguyễn");
        StockReceipt receipt = new StockReceipt();
        ReflectionTestUtils.setField(receipt, "id", 12L);
        StockMovement received = line(30, white, StockMovementReason.RECEIPT, 5, 10);
        received.setReceipt(receipt);
        received.setActor(manager);
        StockMovement sold = line(29, white, StockMovementReason.SALE, -2, 5);
        sold.setOrderId(77L);
        sold.setOrderNumber("EB-77");
        when(movements.findOfProduct(any(), any(), any(), any())).thenReturn(List.of(received, sold));
        when(movements.sumOfVariant(any())).thenReturn(0L);

        var history = service.history(1L, null, null, null, null);

        assertEquals(2, history.items().size());
        var first = history.items().get(0);
        assertEquals("RECEIPT", first.reason());
        assertEquals("PN-000012", first.receiptNumber());
        assertEquals("An Nguyễn", first.actorName());
        assertEquals("Trắng", first.colorName());
        var second = history.items().get(1);
        assertEquals("EB-77", second.orderNumber());
        assertEquals(77L, second.orderId());
        assertNull(second.actorName());
        assertFalse(history.hasMore());
    }

    @Test void oneMoreLineThanThePageMeansThereIsAnotherPageAndTheExtraLineIsNotShown() {
        List<StockMovement> rows = new ArrayList<>();
        for (long id = 50; id >= 20; id--) {
            rows.add(line(id, white, StockMovementReason.SALE, -1, 5));
        }
        when(movements.findOfProduct(any(), any(), any(), any())).thenReturn(rows);

        var history = service.history(1L, null, null, null, 30);

        assertEquals(30, history.items().size());
        assertTrue(history.hasMore());
        assertEquals(50L, history.items().get(0).id());
    }

    @Test void theNextPageSendsTheLastIdAndAsksForNoBalancesAgain() {
        when(movements.findOfProduct(eq(1L), any(), eq(20L), any())).thenReturn(List.of());

        var history = service.history(1L, null, null, 20L, null);

        assertTrue(history.balances().isEmpty());
        verify(movements, never()).sumOfVariant(any());
    }

    @Test void aColourFilterReadsThatColourOnly() {
        when(movements.findOfVariant(eq(12L), any(), any(), any())).thenReturn(List.of());
        when(movements.sumOfVariant(any())).thenReturn(0L);

        service.history(1L, 12L, "SALE,RECEIPT", null, null);

        verify(movements).findOfVariant(eq(12L), eq(java.util.EnumSet.of(StockMovementReason.SALE, StockMovementReason.RECEIPT)), any(), any());
        verify(movements, never()).findOfProduct(any(), any(), any(), any());
    }

    @Test void aColourOfAnotherBikeAnUnknownBikeAndAnUnknownReasonAreRefused() {
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.history(1L, 99L, null, null, null)).getStatusCode().value());
        when(products.findById(5L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.history(5L, null, null, null, null)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.history(1L, null, "GIFT", null, null)).getStatusCode().value());
    }

    @Test void theShellOfAnErasedAccountShowsItsAnonymisedName() {
        User erased = new User();
        erased.setFirstName("Tài khoản");
        erased.setLastName("đã xóa");
        StockMovement adjusted = line(40, white, StockMovementReason.ADJUSTMENT, 1, 6);
        adjusted.setActor(erased);
        when(movements.findOfProduct(any(), any(), any(), any())).thenReturn(List.of(adjusted));
        when(movements.sumOfVariant(any())).thenReturn(0L);

        assertEquals("Tài khoản đã xóa", service.history(1L, null, null, null, null).items().get(0).actorName());
    }
}
