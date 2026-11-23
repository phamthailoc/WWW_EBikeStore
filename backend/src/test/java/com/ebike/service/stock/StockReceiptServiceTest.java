package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.stock.StockDtos.ReceiptCreateRequest;
import com.ebike.dto.stock.StockDtos.ReceiptLineRequest;
import com.ebike.dto.stock.StockDtos.ReceiptResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.model.entity.stock.StockReceipt;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.stock.StockReceiptRepository;
import com.ebike.service.admin.AuditLogService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V26: a receipt adds stock to colours, writes the ledger and tells the customers waiting for a sold-out colour. */
@ExtendWith(MockitoExtension.class)
class StockReceiptServiceTest {
    @Mock StockReceiptRepository receipts;
    @Mock ProductRepository products;
    @Mock StockLedger ledger;
    @Mock StockAlertService stockAlerts;
    @Mock AuditLogService auditLog;
    StockReceiptService service;
    Product product;
    ProductVariant white;
    ProductVariant black;
    ProductVariant retired;
    Product plain;
    User staffMember;

    @BeforeEach void setup() {
        service = new StockReceiptService(receipts, products, ledger, stockAlerts, auditLog);
        product = bike(1L, true, 10);
        white = variant(product, 11L, 4, true);
        black = variant(product, 12L, 0, true);
        retired = variant(product, 13L, 9, false);
        product.getVariants().addAll(List.of(white, black, retired));
        product.setStockQuantity(4);
        plain = bike(2L, true, 7);
        staffMember = new User();
        staffMember.setId(99L);
        staffMember.setUsername("manager");
        lenient().when(products.findById(1L)).thenReturn(Optional.of(product));
        lenient().when(products.findById(2L)).thenReturn(Optional.of(plain));
        lenient().when(ledger.currentStaff()).thenReturn(staffMember);
        lenient().when(receipts.save(any())).thenAnswer(invocation -> {
            StockReceipt receipt = invocation.getArgument(0);
            ReflectionTestUtils.setField(receipt, "id", 12L);
            return receipt;
        });
    }

    private static Product bike(long id, boolean active, int stock) {
        Product bike = new Product();
        ReflectionTestUtils.setField(bike, "id", id);
        bike.setName("DIBAO " + id);
        bike.setActive(active);
        bike.setStockQuantity(stock);
        return bike;
    }

    private static ProductVariant variant(Product owner, long id, int stock, boolean active) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setColorName("Màu " + id);
        variant.setStockQuantity(stock);
        variant.setActive(active);
        variant.setProduct(owner);
        return variant;
    }

    private static ReceiptCreateRequest request(String supplier, ReceiptLineRequest... lines) {
        return new ReceiptCreateRequest(supplier, "Lô hàng tháng 11", List.of(lines));
    }

    private static ReceiptLineRequest line(Long product, Long variant, Integer quantity) {
        return new ReceiptLineRequest(product, variant, quantity);
    }

    @Test void receivingAddsToEachColourAndRecomputesTheBikeTotalFromActiveColours() {
        ReceiptResponse saved = service.create(request("  Công ty   DIBAO ", line(1L, 11L, 6), line(1L, 12L, 3)));

        assertEquals(10, white.getStockQuantity());
        assertEquals(3, black.getStockQuantity());
        // 10 + 3; the retired colour (9) is not for sale and does not count
        assertEquals(13, product.getStockQuantity());
        assertEquals("PN-000012", saved.number());
        assertEquals("Công ty DIBAO", saved.supplierName());
        assertEquals(2, saved.lineCount());
        assertEquals(9, saved.totalQuantity());
        assertEquals("manager", saved.createdByName());
    }

    @Test void theColoursAreLockedBeforeAnythingIsChangedAndTheLinesAreWrittenAfterwards() {
        service.create(request("DIBAO", line(1L, 11L, 6)));

        var inOrder = org.mockito.Mockito.inOrder(ledger, receipts);
        inOrder.verify(ledger).lock(any(), any());
        inOrder.verify(receipts).save(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockLedger.Entry>> entries = ArgumentCaptor.forClass(List.class);
        inOrder.verify(ledger).record(entries.capture(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), any(StockReceipt.class), eq(staffMember));
        assertEquals(1, entries.getValue().size());
        var entry = entries.getValue().get(0);
        assertEquals(6, entry.change());
        assertEquals(10, entry.balanceAfter());
        assertEquals(StockMovementReason.RECEIPT, entry.reason());
        assertEquals(white, entry.variant());
    }

    @Test void aBikeWithoutColoursIsReceivedAtBikeLevel() {
        service.create(request("DIBAO", line(2L, null, 5)));

        assertEquals(12, plain.getStockQuantity());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockLedger.Entry>> entries = ArgumentCaptor.forClass(List.class);
        verify(ledger).record(entries.capture(), any(), any(), any(), any());
        assertEquals(null, entries.getValue().get(0).variant());
        assertEquals(12, entries.getValue().get(0).balanceAfter());
    }

    @Test void aSoldOutColourBroughtBackTellsTheCustomersWaiting() {
        service.create(request("DIBAO", line(1L, 12L, 3)));
        verify(stockAlerts).notifyRestocked(black);
    }

    @Test void toppingUpAColourAlreadyInStockTellsNobody() {
        service.create(request("DIBAO", line(1L, 11L, 3)));
        verify(stockAlerts, never()).notifyRestocked(any());
    }

    @Test void aRetiredColourOrAWithdrawnBikeTellsNobody() {
        retired.setStockQuantity(0);
        service.create(request("DIBAO", line(1L, 13L, 2)));
        verify(stockAlerts, never()).notifyRestocked(any());

        product.setActive(false);
        black.setStockQuantity(0);
        service.create(request("DIBAO", line(1L, 12L, 2)));
        verify(stockAlerts, never()).notifyRestocked(any());
    }

    @Test void aBikeSoldByColourCannotBeReceivedWithoutAColour() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(request("DIBAO", line(1L, null, 2))));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Dòng 1"));
        verify(receipts, never()).save(any());
    }

    @Test void aColourOfAnotherBikeIsRefused() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(request("DIBAO", line(2L, 11L, 2))));
        assertEquals(400, ex.getStatusCode().value());
        verify(receipts, never()).save(any());
    }

    @Test void anUnknownBikeIsRefused() {
        when(products.findById(77L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.create(request("DIBAO", line(77L, null, 2))));
        verify(receipts, never()).save(any());
    }

    @Test void aBadLineLateInTheReceiptTouchesNoStockAtAll() {
        assertThrows(ResponseStatusException.class, () -> service.create(request("DIBAO", line(1L, 11L, 5), line(1L, 12L, 0))));
        assertEquals(4, white.getStockQuantity());
        assertEquals(4, product.getStockQuantity());
        verify(ledger, never()).lock(any(), any());
        verify(ledger, never()).record(any(), any(), any(), any(), any());
    }

    @Test void theSameColourTwiceIsRefused() {
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(request("DIBAO", line(1L, 11L, 1), line(1L, 11L, 2))));
        assertTrue(ex.getReason().contains("Dòng 2"));
    }

    @Test void stockThatWouldPassTheCeilingIsRefusedWithoutChangingAnything() {
        white.setStockQuantity(99_500);
        var ex = assertThrows(ResponseStatusException.class, () -> service.create(request("DIBAO", line(1L, 12L, 5), line(1L, 11L, 600))));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Dòng 2"));
        assertEquals(99_500, white.getStockQuantity());
        // the first line was fine on its own, but nothing is applied when a later line is refused
        assertEquals(0, black.getStockQuantity());
        verify(receipts, never()).save(any());
    }

    @Test void theSupplierIsRequiredAndBounded() {
        assertThrows(ResponseStatusException.class, () -> service.create(request("   ", line(1L, 11L, 1))));
        assertThrows(ResponseStatusException.class, () -> service.create(request(null, line(1L, 11L, 1))));
        assertThrows(ResponseStatusException.class, () -> service.create(request("x".repeat(151), line(1L, 11L, 1))));
        assertThrows(ResponseStatusException.class, () -> service.create(new ReceiptCreateRequest("DIBAO", "n".repeat(501), List.of(line(1L, 11L, 1)))));
        assertThrows(ResponseStatusException.class, () -> service.create(null));
        assertThrows(ResponseStatusException.class, () -> service.create(new ReceiptCreateRequest("DIBAO", null, null)));
        verify(receipts, never()).save(any());
    }

    @Test void theReceiptIsWrittenInTheAuditTrailWithItsNumber() {
        service.create(request("DIBAO", line(1L, 11L, 6)));
        verify(auditLog).record("nhập kho", "PN-000012: DIBAO (6 xe)");
    }

    @Test void searchingByAReceiptNumberFindsThatReceiptOnly() {
        StockReceipt receipt = new StockReceipt();
        ReflectionTestUtils.setField(receipt, "id", 12L);
        receipt.setSupplierName("DIBAO");
        when(receipts.findDetailById(12L)).thenReturn(Optional.of(receipt));

        var page = service.list("PN-000012", null, null);

        assertEquals(1, page.items().size());
        assertEquals("PN-000012", page.items().get(0).number());
        assertEquals(false, page.hasMore());
        verify(receipts, never()).search(any(), any(), any());
    }

    @Test void theListSaysWhenThereIsAnotherPage() {
        List<StockReceipt> rows = new java.util.ArrayList<>();
        for (long id = 30; id >= 10; id--) {
            StockReceipt receipt = new StockReceipt();
            ReflectionTestUtils.setField(receipt, "id", id);
            receipt.setSupplierName("DIBAO");
            rows.add(receipt);
        }
        when(receipts.search(eq(Long.MAX_VALUE), eq("%dibao%"), any())).thenReturn(rows);

        var page = service.list("Dibao", null, 20);

        assertEquals(20, page.items().size());
        assertEquals(true, page.hasMore());
    }

    @Test void aReceiptThatDoesNotExistIsNotFound() {
        when(receipts.findDetailById(5L)).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> service.get(5L));
        assertEquals(404, ex.getStatusCode().value());
    }
}
