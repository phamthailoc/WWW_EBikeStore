package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.stock.StockDtos.OptionProduct;
import com.ebike.dto.stock.StockDtos.ReceiptCreateRequest;
import com.ebike.dto.stock.StockDtos.ReceiptLineRequest;
import com.ebike.dto.stock.StockDtos.ReceiptPage;
import com.ebike.dto.stock.StockDtos.ReceiptResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.model.entity.stock.StockReceipt;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.stock.StockReceiptRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.stock.StockAlertService;
import com.ebike.service.stock.StockLedger;
import com.ebike.service.stock.StockReceiptService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class StockReceiptServiceTest {

    private StockReceiptRepository receipts;
    private ProductRepository products;
    private StockLedger ledger;
    private StockAlertService alerts;
    private AuditLogService audit;
    private StockReceiptService service;
    private Product bike;
    private ProductVariant red;
    private ProductVariant blue;
    private ProductVariant retired;

    @BeforeEach
    void setUp() {
        receipts = mock(StockReceiptRepository.class);
        products = mock(ProductRepository.class);
        ledger = mock(StockLedger.class);
        alerts = mock(StockAlertService.class);
        audit = mock(AuditLogService.class);
        service = new StockReceiptService(receipts, products, ledger, alerts, audit);
        bike = Fx.product(1, "City Bike", 5);
        red = Fx.variant(bike, 11, "Red", 0, true);
        blue = Fx.variant(bike, 12, "Blue", 5, true);
        retired = Fx.variant(bike, 13, "Old", 7, false);
        bike.setStockQuantity(5);
        when(products.findById(1L)).thenReturn(Optional.of(bike));
        when(receipts.save(any(StockReceipt.class))).thenAnswer(i -> {
            StockReceipt r = i.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 12L);
            return r;
        });
    }

    private static ReceiptCreateRequest req(String supplier, ReceiptLineRequest... lines) {
        return new ReceiptCreateRequest(supplier, null, List.of(lines));
    }

    private static ReceiptLineRequest l(Long p, Long v, Integer q) {
        return new ReceiptLineRequest(p, v, q);
    }

    private static int status(Runnable r) {
        try {
            r.run();
        } catch (ResponseStatusException e) {
            return e.getStatusCode().value();
        }
        return 0;
    }

    @Test
    void receiptAddsStockToColourAndRecomputesBikeTotalFromActiveColoursOnly() {
        ReceiptResponse r = service.create(req("  ACME   Co ", l(1L, 11L, 10)));
        assertThat(red.getStockQuantity()).isEqualTo(10);
        assertThat(blue.getStockQuantity()).isEqualTo(5);
        assertThat(retired.getStockQuantity()).isEqualTo(7);
        assertThat(bike.getStockQuantity()).isEqualTo(15); // 10 + 5, withdrawn colour not counted
        assertThat(r.number()).isEqualTo("PN-000012");
        assertThat(r.supplierName()).isEqualTo("ACME Co");
        assertThat(r.totalQuantity()).isEqualTo(10);
        assertThat(r.lineCount()).isEqualTo(1);
        verify(audit).record(eq("nhập kho"), anyString());
    }

    @Test
    void ledgerLinesWrittenWithPositiveChangeAndBalanceAfter() {
        service.create(req("S", l(1L, 11L, 10), l(1L, 12L, 3)));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockLedger.Entry>> cap = ArgumentCaptor.forClass(List.class);
        verify(ledger).record(cap.capture(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), any(StockReceipt.class), org.mockito.ArgumentMatchers.isNull());
        assertThat(cap.getValue()).hasSize(2);
        assertThat(cap.getValue()).allSatisfy(e -> assertThat(e.reason()).isEqualTo(StockMovementReason.RECEIPT));
        assertThat(cap.getValue().get(0).change()).isEqualTo(10);
        assertThat(cap.getValue().get(0).balanceAfter()).isEqualTo(10);
        assertThat(cap.getValue().get(1).balanceAfter()).isEqualTo(8);
    }

    @Test
    void locksBikesAndAllColoursBeforeChangingStock() {
        service.create(req("S", l(1L, 11L, 1)));
        org.mockito.InOrder order = Mockito.inOrder(ledger, receipts);
        order.verify(ledger).lock(anyCollection(), anyCollection());
        order.verify(receipts).save(any());
    }

    @Test
    void restockOfSoldOutColourAlertsWaitingCustomersOnce() {
        service.create(req("S", l(1L, 11L, 4)));
        verify(alerts, times(1)).notifyRestocked(red);
    }

    @Test
    void topUpOfColourAlreadyInStockTellsNobody() {
        service.create(req("S", l(1L, 12L, 4)));
        verify(alerts, never()).notifyRestocked(any());
    }

    @Test
    void inactiveColourOrInactiveBikeTellsNobody() {
        retired.setStockQuantity(0);
        service.create(req("S", l(1L, 13L, 4)));
        verify(alerts, never()).notifyRestocked(any());
        bike.setActive(false);
        service.create(req("S", l(1L, 11L, 4)));
        verify(alerts, never()).notifyRestocked(any());
    }

    @Test
    void supplierRequiredAndLimited() {
        assertThat(status(() -> service.create(req(null, l(1L, 11L, 1))))).isEqualTo(400);
        assertThat(status(() -> service.create(req("   ", l(1L, 11L, 1))))).isEqualTo(400);
        assertThat(status(() -> service.create(req("x".repeat(151), l(1L, 11L, 1))))).isEqualTo(400);
        service.create(req("x".repeat(150), l(1L, 11L, 1)));
        assertThat(status(() -> service.create(null))).isEqualTo(400);
        assertThat(status(() -> service.create(new ReceiptCreateRequest("S", null, null)))).isEqualTo(400);
    }

    @Test
    void noteLimit() {
        assertThat(status(() -> service.create(new ReceiptCreateRequest("S", "n".repeat(501), List.of(l(1L, 11L, 1)))))).isEqualTo(400);
        ReceiptResponse ok = service.create(new ReceiptCreateRequest("S", "  " + "n".repeat(500) + "  ", List.of(l(1L, 11L, 1))));
        assertThat(ok.note()).hasSize(500);
    }

    @Test
    void invalidLinesNeverTouchStock() {
        assertThat(status(() -> service.create(req("S", l(1L, 11L, 0))))).isEqualTo(400);
        assertThat(status(() -> service.create(req("S", l(1L, 11L, -1))))).isEqualTo(400);
        assertThat(status(() -> service.create(req("S", l(1L, 11L, 1001))))).isEqualTo(400);
        assertThat(status(() -> service.create(req("S", l(1L, 11L, 1), l(1L, 11L, 1))))).isEqualTo(400);
        assertThat(status(() -> service.create(req("S")))).isEqualTo(400);
        assertThat(red.getStockQuantity()).isZero();
        verify(receipts, never()).save(any());
        verify(ledger, never()).lock(any(), any());
    }

    @Test
    void unknownBikeOrForeignColourRefusedBeforeAnyStockChanges() {
        when(products.findById(99L)).thenReturn(Optional.empty());
        assertThat(status(() -> service.create(req("S", l(1L, 11L, 5), l(99L, null, 1))))).isEqualTo(400);
        Product other = Fx.product(2, "Other", 0);
        Fx.variant(other, 21, "Green", 0, true);
        when(products.findById(2L)).thenReturn(Optional.of(other));
        assertThat(status(() -> service.create(req("S", l(1L, 11L, 5), l(2L, 11L, 1))))).isEqualTo(400);
        assertThat(red.getStockQuantity()).isZero();
        verify(receipts, never()).save(any());
    }

    @Test
    void bikeWithColoursNeedsColourChosen() {
        assertThat(status(() -> service.create(req("S", l(1L, null, 1))))).isEqualTo(400);
    }

    @Test
    void bikeWithoutActiveColoursReceivedAsWhole() {
        Product plain = Fx.product(3, "Plain", 2);
        Fx.variant(plain, 31, "Old", 9, false);
        when(products.findById(3L)).thenReturn(Optional.of(plain));
        service.create(req("S", l(3L, null, 6)));
        assertThat(plain.getStockQuantity()).isEqualTo(8);
    }

    @Test
    void shelfLimitIsEnforcedPerLineAtExactBoundary() {
        red.setStockQuantity(99_000);
        service.create(req("S", l(1L, 11L, 1000)));
        assertThat(red.getStockQuantity()).isEqualTo(100_000);
        assertThat(status(() -> service.create(req("S", l(1L, 11L, 1))))).isEqualTo(400);
        assertThat(red.getStockQuantity()).isEqualTo(100_000);
    }

    @Test
    void shelfLimitFailureOnSecondLineLeavesFirstLineUntouched() {
        blue.setStockQuantity(100_000);
        assertThat(status(() -> service.create(req("S", l(1L, 11L, 5), l(1L, 12L, 1))))).isEqualTo(400);
        assertThat(red.getStockQuantity()).isZero();
        verify(receipts, never()).save(any());
    }

    @Test
    void stockNeverGoesNegativeOrNullBecomesZeroBased() {
        red.setStockQuantity(null);
        service.create(req("S", l(1L, 11L, 2)));
        assertThat(red.getStockQuantity()).isEqualTo(2);
        verify(alerts).notifyRestocked(red);
    }

    @Test
    void receiptLineKeepsNameSnapshotAndTruncatesColourTo100() {
        red.setColorName("c".repeat(150));
        ReceiptResponse r = service.create(req("S", l(1L, 11L, 1)));
        assertThat(r.lines().get(0).colorName()).hasSize(100);
        assertThat(r.lines().get(0).productName()).isEqualTo("City Bike");
    }

    @Test
    void createdByIsStaffMember() {
        User staff = Fx.user(3, "mgr");
        staff.setFirstName("Man");
        staff.setLastName("Ager");
        when(ledger.currentStaff()).thenReturn(staff);
        assertThat(service.create(req("S", l(1L, 11L, 1))).createdByName()).isEqualTo("Man Ager");
    }

    // ---- list / get / options ----
    @Test
    void listClampsLimitAndDetectsHasMore() {
        List<StockReceipt> rows = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            StockReceipt r = new StockReceipt();
            ReflectionTestUtils.setField(r, "id", (long) (100 - i));
            r.setSupplierName("s");
            rows.add(r);
        }
        when(receipts.search(any(), anyString(), any(Pageable.class))).thenReturn(rows);
        ReceiptPage page = service.list(null, null, null);
        assertThat(page.items()).hasSize(20);
        assertThat(page.hasMore()).isTrue();
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(receipts).search(eq(Long.MAX_VALUE), eq("%"), p.capture());
        assertThat(p.getValue().getPageSize()).isEqualTo(21);

        service.list(null, 5L, 9999);
        verify(receipts).search(eq(5L), eq("%"), Mockito.argThat(x -> x.getPageSize() == 51));
        service.list(null, -3L, 0);
        verify(receipts).search(eq(Long.MAX_VALUE), eq("%"), Mockito.argThat(x -> x.getPageSize() == 2));
    }

    @Test
    void listByReceiptNumberUsesDetailLookupAndHonoursBeforeId() {
        StockReceipt r = new StockReceipt();
        ReflectionTestUtils.setField(r, "id", 12L);
        r.setSupplierName("s");
        when(receipts.findDetailById(12L)).thenReturn(Optional.of(r));
        assertThat(service.list("PN-000012", null, null).items()).hasSize(1);
        assertThat(service.list("pn12", 12L, null).items()).isEmpty(); // 12 is not < 12
        assertThat(service.list("pn12", 13L, null).items()).hasSize(1);
        assertThat(service.list("PN-999", null, null).items()).isEmpty();
        verify(receipts, never()).search(any(), anyString(), any());
    }

    @Test
    void listSupplierTextIsSanitisedForLike() {
        when(receipts.search(any(), anyString(), any())).thenReturn(List.of());
        service.list("100%_x", null, null);
        verify(receipts).search(any(), eq("%100  x%"), any());
    }

    @Test
    void getMissingIs404() {
        when(receipts.findDetailById(5L)).thenReturn(Optional.empty());
        assertThat(status(() -> service.get(5L))).isEqualTo(404);
    }

    @Test
    void optionsSortDefaultColourFirstAndNameFallsBackToVariantName() {
        red.setDefaultVariant(false);
        blue.setDefaultVariant(true);
        retired.setColorName(null);
        retired.setVariantName("Legacy");
        when(products.findAllWithVariants()).thenReturn(List.of(bike));
        List<OptionProduct> out = service.options();
        assertThat(out.get(0).colours().get(0).id()).isEqualTo(12L);
        assertThat(out.get(0).hasActiveColours()).isTrue();
        assertThat(out.get(0).colours()).anySatisfy(c -> assertThat(c.colorName()).isEqualTo("Legacy"));
    }
}
