package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.product.response.StockAlertResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.StockAlert;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.repository.product.StockAlertRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** V22: one waiting row per customer and colour; restocking arms nobody twice. */
@ExtendWith(MockitoExtension.class)
class StockAlertServiceTest {
    @Mock StockAlertRepository alerts;
    @Mock UserRepository users;
    @Mock ProductVariantRepository variants;
    @Mock StockAlertDelivery delivery;
    StockAlertService service;
    User user;
    ProductVariant variant;

    @BeforeEach void setup() {
        service = new StockAlertService(alerts, users, variants, delivery);
        user = new User();
        user.setId(7L);
        Product product = new Product();
        product.setId(1L);
        product.setActive(true);
        variant = new ProductVariant();
        variant.setId(11L);
        variant.setActive(true);
        variant.setColorName("Trắng");
        variant.setStockQuantity(0);
        variant.setProduct(product);
        product.setName("Kinetic X");
        product.setSlug("kinetic-x");
        org.mockito.Mockito.lenient().when(users.findByUsername("an")).thenReturn(Optional.of(user));
        org.mockito.Mockito.lenient().when(variants.findStockOnSale(11L)).thenReturn(Optional.of(0));
        org.mockito.Mockito.lenient().when(alerts.findResponseByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(row(null)));
        org.mockito.Mockito.lenient().when(alerts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private StockAlertResponse row(java.time.LocalDateTime notifiedAt) {
        return new StockAlertResponse(5L, 1L, "Kinetic X", "kinetic-x", 11L, "Trắng", 0, java.time.LocalDateTime.now(), notifiedAt, true);
    }

    @Test void subscribingToASoldOutColourSavesOneRow() {
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        service.subscribe("an", 11L);
        verify(alerts).save(any(StockAlert.class));
    }

    @Test void subscribingTwiceDoesNotCreateADuplicate() {
        StockAlert existing = new StockAlert();
        existing.setUser(user);
        existing.setVariant(variant);
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(existing));
        service.subscribe("an", 11L);
        verify(alerts, never()).save(any());
    }

    @Test void colourStillInStockCannotBeWaitedFor() {
        when(variants.findStockOnSale(11L)).thenReturn(Optional.of(3));
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.subscribe("an", 11L));
    }

    @Test void usedRowIsArmedAgainOnNextSubscribe() {
        StockAlert used = new StockAlert();
        used.setUser(user);
        used.setVariant(variant);
        used.setNotifiedAt(java.time.LocalDateTime.now());
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(used));
        service.subscribe("an", 11L);
        assertNull(used.getNotifiedAt());
    }

    @Test void aRaceOnTheUniqueKeyReturnsTheRowTheOtherRequestCreated() {
        StockAlert winner = new StockAlert();
        winner.setUser(user);
        winner.setVariant(variant);
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        when(alerts.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        assertNotNull(service.subscribe("an", 11L));
    }

    @Test void aFailureWhileTellingNeverReachesTheCaller() {
        when(delivery.deliver(11L)).thenThrow(new IllegalStateException("db down"));
        service.notifyRestocked(variant);
        verify(delivery).deliver(11L);
    }

    @Test void insideATransactionCustomersAreToldOnlyAfterItCommits() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.notifyRestocked(variant);
            verify(delivery, never()).deliver(any(Long.class));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(delivery).deliver(11L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void aRestockCommittedWhileSubscribingTellsTheCustomerAtOnce() {
        // the first read returned stock 0; the scalar re-read sees the committed restock
        when(variants.isOnSaleAndInStock(11L)).thenReturn(true);
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        service.subscribe("an", 11L);
        verify(delivery).deliver(11L);
    }

    @Test void subscribingToAColourThatStaysSoldOutTellsNobody() {
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        service.subscribe("an", 11L);
        verify(delivery, never()).deliver(any(Long.class));
    }

    @Test void unsubscribingDeletesOnlyTheCallersOwnRow() {
        StockAlert mine = new StockAlert();
        mine.setUser(user);
        mine.setVariant(variant);
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(mine));
        service.unsubscribe("an", 11L);
        verify(alerts).delete(mine);
    }

    @Test void unsubscribingWithoutARowOfYourOwnDeletesNothing() {
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        service.unsubscribe("an", 11L);
        verify(alerts, never()).delete(any());
        verify(alerts, never()).deleteByUserId(any());
    }

    @Test void listComesStraightFromTheProjectionQuery() {
        StockAlertResponse live = row(null);
        when(alerts.findResponsesByUsername("an")).thenReturn(List.of(live));
        assertEquals(List.of(live), service.list("an"));
        verify(variants, never()).findWithProductById(any());
    }

    @Test void subscribingNeverLoadsTheBikeAsAnEntity() {
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        service.subscribe("an", 11L);
        verify(variants, never()).findWithProductById(any());
    }

    @Test void aColourThatIsWithdrawnCannotBeWaitedFor() {
        when(variants.findStockOnSale(11L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.subscribe("an", 11L));
        verify(alerts, never()).save(any());
    }
}
