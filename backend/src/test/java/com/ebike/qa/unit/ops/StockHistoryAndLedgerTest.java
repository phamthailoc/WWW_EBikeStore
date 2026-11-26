package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.stock.StockDtos.HistoryResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.stock.StockMovement;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.model.entity.stock.StockReceipt;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.stock.StockMovementRepository;
import com.ebike.service.stock.StockHistoryService;
import com.ebike.service.stock.StockLedger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class StockHistoryAndLedgerTest {

    private ProductRepository products;
    private StockMovementRepository movements;
    private UserRepository users;
    private StockHistoryService history;
    private StockLedger ledger;
    private EntityManager em;
    private Product bike;
    private ProductVariant red;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        movements = mock(StockMovementRepository.class);
        users = mock(UserRepository.class);
        em = mock(EntityManager.class);
        history = new StockHistoryService(products, movements);
        ledger = new StockLedger(movements, users);
        ReflectionTestUtils.setField(ledger, "entityManager", em);
        bike = Fx.product(1, "Bike", 4);
        red = Fx.variant(bike, 11, "Red", 4, true);
        when(products.findById(1L)).thenReturn(Optional.of(bike));
        when(movements.sumOfVariant(11L)).thenReturn(4L);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static int status(Runnable r) {
        try {
            r.run();
        } catch (ResponseStatusException e) {
            return e.getStatusCode().value();
        }
        return 0;
    }

    private static StockMovement mv(long id) {
        StockMovement m = new StockMovement();
        ReflectionTestUtils.setField(m, "id", id);
        m.setReason(StockMovementReason.SALE);
        m.setQuantityChange(-1);
        return m;
    }

    // ---------------- history ----------------
    @Test
    void unknownBikeOrColour404() {
        when(products.findById(9L)).thenReturn(Optional.empty());
        assertThat(status(() -> history.history(9L, null, null, null, null))).isEqualTo(404);
        assertThat(status(() -> history.history(1L, 999L, null, null, null))).isEqualTo(404);
    }

    @Test
    void badReasonIs400() {
        assertThat(status(() -> history.history(1L, null, "SALE,FOO", null, null))).isEqualTo(400);
    }

    @Test
    void firstPageHasBalancesAndHasMoreFlag() {
        List<StockMovement> rows = new ArrayList<>();
        for (int i = 0; i < 31; i++) rows.add(mv(100 - i));
        when(movements.findOfProduct(eq(1L), any(), eq(Long.MAX_VALUE), any(Pageable.class))).thenReturn(rows);
        HistoryResponse r = history.history(1L, null, null, null, null);
        assertThat(r.items()).hasSize(30);
        assertThat(r.hasMore()).isTrue();
        assertThat(r.balances()).hasSize(1);
        assertThat(r.balances().get(0).matches()).isTrue();
        assertThat(r.balances().get(0).ledgerTotal()).isEqualTo(4L);
    }

    @Test
    void balanceMismatchIsReported() {
        when(movements.sumOfVariant(11L)).thenReturn(7L);
        when(movements.findOfProduct(anyLong(), any(), anyLong(), any())).thenReturn(List.of());
        assertThat(history.history(1L, null, null, null, null).balances().get(0).matches()).isFalse();
    }

    @Test
    void laterPagesSkipBalances() {
        when(movements.findOfProduct(anyLong(), any(), anyLong(), any())).thenReturn(List.of());
        assertThat(history.history(1L, null, null, 50L, null).balances()).isEmpty();
        verify(movements, never()).sumOfVariant(anyLong());
    }

    @Test
    @Tag("bug")
    void bug_beforeIdZeroIsFirstPageSoItShouldCarryBalances() {
        // Expected: beforeId<=0 is normalised to "first page" (Long.MAX_VALUE) so the balances should be returned like page one.
        // Actual: balances are skipped because the check is `beforeId == null`.
        // StockHistoryService.java:324
        when(movements.findOfProduct(anyLong(), any(), anyLong(), any())).thenReturn(List.of());
        assertThat(history.history(1L, null, null, 0L, null).balances()).isNotEmpty();
    }

    @Test
    void limitIsClampedAndColourFilterUsesVariantQuery() {
        when(movements.findOfVariant(anyLong(), any(), anyLong(), any())).thenReturn(List.of());
        history.history(1L, 11L, "sale", 7L, 100000);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(movements).findOfVariant(eq(11L), any(), eq(7L), p.capture());
        assertThat(p.getValue().getPageSize()).isEqualTo(101);
        history.history(1L, 11L, null, null, -4);
        verify(movements).findOfVariant(eq(11L), any(), eq(Long.MAX_VALUE), Mockito.argThat(x -> x.getPageSize() == 2));
    }

    @Test
    void bikeWithoutColoursReportsProductLevelBalance() {
        Product plain = Fx.product(2, "Plain", 3);
        when(products.findById(2L)).thenReturn(Optional.of(plain));
        when(movements.sumOfProductLevel(2L)).thenReturn(3L);
        when(movements.findOfProduct(anyLong(), any(), anyLong(), any())).thenReturn(List.of());
        HistoryResponse r = history.history(2L, null, null, null, null);
        assertThat(r.balances()).hasSize(1);
        assertThat(r.balances().get(0).variantId()).isNull();
        assertThat(r.balances().get(0).matches()).isTrue();
    }

    @Test
    void movementResponseShowsReceiptNumberAndActorName() {
        StockMovement m = mv(1);
        StockReceipt rc = new StockReceipt();
        ReflectionTestUtils.setField(rc, "id", 7L);
        m.setReceipt(rc);
        m.setVariant(red);
        User a = Fx.user(5, "bob");
        m.setActor(a);
        when(movements.findOfProduct(anyLong(), any(), anyLong(), any())).thenReturn(List.of(m));
        var item = history.history(1L, null, null, 5L, null).items().get(0);
        assertThat(item.receiptNumber()).isEqualTo("PN-000007");
        assertThat(item.actorName()).isEqualTo("bob");
        assertThat(item.colorName()).isEqualTo("Red");
    }

    // ---------------- ledger.record ----------------
    @Test
    void recordSkipsZeroChangesAndNullProduct() {
        ledger.record(List.of(
            StockLedger.Entry.of(bike, red, 0, StockMovementReason.ADJUSTMENT, null),
            new StockLedger.Entry(null, null, 5, 5, StockMovementReason.RECEIPT, null)), null, null, null, null);
        verify(movements, never()).saveAll(any());
        ledger.record(null, null, null, null, null);
        ledger.record(List.of(), null, null, null, null);
        verify(movements, never()).saveAll(any());
    }

    @Test
    void recordWritesSignedLinesWithCleanNote() {
        red.setStockQuantity(2);
        User staff = Fx.user(3, "s");
        ledger.record(List.of(StockLedger.Entry.of(bike, red, -2, StockMovementReason.SALE, "  a   b ")), 9L, "ORD-9", null, staff);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockMovement>> cap = ArgumentCaptor.forClass(List.class);
        verify(movements).saveAll(cap.capture());
        StockMovement m = cap.getValue().get(0);
        assertThat(m.getQuantityChange()).isEqualTo(-2);
        assertThat(m.getBalanceAfter()).isEqualTo(2);
        assertThat(m.getNote()).isEqualTo("a b");
        assertThat(m.getOrderNumber()).isEqualTo("ORD-9");
        assertThat(m.getActor()).isSameAs(staff);
    }

    @Test
    void entryBalanceUsesColourThenBikeThenZero() {
        assertThat(StockLedger.Entry.of(bike, red, 1, StockMovementReason.RECEIPT, null).balanceAfter()).isEqualTo(4);
        assertThat(StockLedger.Entry.of(bike, null, 1, StockMovementReason.RECEIPT, null).balanceAfter()).isEqualTo(4);
        bike.setStockQuantity(null);
        assertThat(StockLedger.Entry.of(bike, null, 1, StockMovementReason.RECEIPT, null).balanceAfter()).isZero();
    }

    // ---------------- ledger.currentStaff ----------------
    @Test
    void currentStaffAbsentOrAnonymous() {
        assertThat(ledger.currentStaff()).isNull();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));
        assertThat(ledger.currentStaff()).isNull();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser", "x", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("A"))));
        assertThat(ledger.currentStaff()).isNull();
        verify(users, never()).findByUsername(any());
    }

    @Test
    void currentStaffFindsByUsernameThenEmail() {
        User u = Fx.user(4, "mgr");
        when(users.findByUsername("mgr@x")).thenReturn(Optional.empty());
        when(users.findByEmail("mgr@x")).thenReturn(Optional.of(u));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("mgr@x", "p", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("A"))));
        assertThat(ledger.currentStaff()).isSameAs(u);
    }

    // ---------------- ledger.lock ----------------
    @Test
    void lockTakesBikesBeforeColoursInIdOrderAndSkipsHeldRows() {
        Product b2 = Fx.product(2, "B2", 1);
        ProductVariant v21 = Fx.variant(b2, 21, "X", 1, true);
        when(em.contains(any())).thenReturn(true);
        when(em.getLockMode(any())).thenReturn(LockModeType.NONE);
        ledger.lock(List.of(b2, bike), List.of(v21, red));
        InOrder order = Mockito.inOrder(em);
        order.verify(em).refresh(bike, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).refresh(b2, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).refresh(red, LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).refresh(v21, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void lockIsIdempotentForRowsAlreadyLocked() {
        when(em.contains(any())).thenReturn(true);
        when(em.getLockMode(any())).thenReturn(LockModeType.PESSIMISTIC_WRITE);
        ledger.lock(List.of(bike), List.of(red));
        verify(em, never()).refresh(any(), any(LockModeType.class));
        verify(em, never()).flush();
    }

    @Test
    void lockFlushesBeforeRefreshAndRejectsDetachedRow() {
        when(em.contains(any())).thenReturn(true);
        when(em.getLockMode(any())).thenReturn(LockModeType.NONE);
        ledger.lock(List.of(bike), null);
        InOrder order = Mockito.inOrder(em);
        order.verify(em).flush();
        order.verify(em).refresh(bike, LockModeType.PESSIMISTIC_WRITE);
        when(em.contains(bike)).thenReturn(false);
        assertThatThrownBy(() -> ledger.lock(List.of(bike), null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lockToleratesNullsAndUnsavedRows() {
        ProductVariant unsaved = new ProductVariant();
        ledger.lock(null, null);
        ledger.lock(java.util.Arrays.asList((Product) null), java.util.Arrays.asList(null, unsaved));
        verify(em, never()).refresh(any(), any(LockModeType.class));
    }
}
