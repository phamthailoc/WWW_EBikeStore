package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.stock.StockMovement;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.stock.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/** V26: the ledger takes its row locks in one fixed order and writes one line per change. */
@ExtendWith(MockitoExtension.class)
class StockLedgerTest {
    @Mock StockMovementRepository movements;
    @Mock UserRepository users;
    @Mock EntityManager entityManager;
    StockLedger ledger;

    @BeforeEach void setup() {
        ledger = new StockLedger(movements, users);
        ReflectionTestUtils.setField(ledger, "entityManager", entityManager);
        lenient().when(entityManager.contains(any())).thenReturn(true);
        lenient().when(entityManager.getLockMode(any())).thenReturn(LockModeType.NONE);
    }

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Product bike(long id) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setStockQuantity(0);
        return product;
    }

    private static ProductVariant colour(Product owner, long id) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setProduct(owner);
        variant.setStockQuantity(0);
        return variant;
    }

    @Test void bikesAreLockedBeforeColoursAndEachInIdOrderWhateverTheOrderGiven() {
        Product b3 = bike(3);
        Product b1 = bike(1);
        ProductVariant c30 = colour(b3, 30);
        ProductVariant c10 = colour(b1, 10);

        ledger.lock(List.of(b3, b1, b3), List.of(c30, c10, c30));

        InOrder order = inOrder(entityManager);
        order.verify(entityManager).refresh(b1, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entityManager).refresh(b3, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entityManager).refresh(c10, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entityManager).refresh(c30, LockModeType.PESSIMISTIC_WRITE);
        // each row once, though it was named twice
        verify(entityManager, org.mockito.Mockito.times(4)).refresh(any(), any(LockModeType.class));
    }

    @Test void theBikeOfALockedColourIsLockedToo() {
        Product b2 = bike(2);
        ProductVariant c20 = colour(b2, 20);

        ledger.lock(List.of(), List.of(c20));

        InOrder order = inOrder(entityManager);
        order.verify(entityManager).refresh(b2, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entityManager).refresh(c20, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test void aRowAlreadyLockedInThisTransactionIsNotReadAgainSoPendingChangesSurvive() {
        Product b1 = bike(1);
        when(entityManager.getLockMode(b1)).thenReturn(LockModeType.PESSIMISTIC_WRITE);

        ledger.lock(List.of(b1), List.of());

        verify(entityManager, never()).refresh(any(), any(LockModeType.class));
    }

    @Test void nothingToLockIsNotAnError() {
        ledger.lock(null, null);
        ledger.lock(List.of(), List.of());
        verify(entityManager, never()).refresh(any(), any(LockModeType.class));
    }

    @Test void aRowThatIsNotLoadedInThisTransactionIsAProgrammingError() {
        Product b1 = bike(1);
        when(entityManager.contains(b1)).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> ledger.lock(List.of(b1), List.of()));
    }

    @Test void oneLineIsWrittenPerChangeAndZeroChangesAreSkipped() {
        Product b1 = bike(1);
        ProductVariant c10 = colour(b1, 10);
        c10.setStockQuantity(7);
        User actor = new User();
        actor.setId(5L);

        ledger.record(List.of(
            StockLedger.Entry.of(b1, c10, 3, StockMovementReason.RECEIPT, "  Lô  tháng 11 "),
            StockLedger.Entry.of(b1, c10, 0, StockMovementReason.ADJUSTMENT, null)),
            null, null, null, actor);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockMovement>> saved = ArgumentCaptor.forClass(List.class);
        verify(movements).saveAll(saved.capture());
        assertEquals(1, saved.getValue().size());
        StockMovement line = saved.getValue().get(0);
        assertEquals(3, line.getQuantityChange());
        assertEquals(7, line.getBalanceAfter());
        assertEquals(StockMovementReason.RECEIPT, line.getReason());
        assertEquals("Lô tháng 11", line.getNote());
        assertEquals(c10, line.getVariant());
        assertEquals(actor, line.getActor());
    }

    @Test void aBikeWithoutColoursReadsItsOwnStockAsTheBalance() {
        Product b1 = bike(1);
        b1.setStockQuantity(12);
        assertEquals(12, StockLedger.Entry.of(b1, null, -2, StockMovementReason.SALE, null).balanceAfter());
    }

    @Test void nothingIsWrittenForAnEmptyList() {
        ledger.record(List.of(), 1L, "EB-1", null, null);
        ledger.record(null, 1L, "EB-1", null, null);
        verify(movements, never()).saveAll(any());
    }

    @Test void nobodyIsSignedInMeansNoStaffMember() {
        assertNull(ledger.currentStaff());
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertNull(ledger.currentStaff());
    }

    @Test void theSignedInStaffMemberIsFoundByUsername() {
        User manager = new User();
        manager.setId(99L);
        when(users.findByUsername("manager")).thenReturn(Optional.of(manager));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("manager", "x", List.of()));
        assertEquals(manager, ledger.currentStaff());
        verify(users, never()).findByEmail(any());
    }
}
