package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.product.response.StockAlertResponse;
import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.StockAlert;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.repository.product.StockAlertRecipient;
import com.ebike.repository.product.StockAlertRepository;
import com.ebike.service.stock.StockAlertDelivery;
import com.ebike.service.stock.StockAlertService;
import com.ebike.service.stock.StockAlertSubscription;
import com.ebike.service.stock.StockAlertSweepJob;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

class StockAlertTest {

    private StockAlertRepository alertRepo;
    private UserRepository userRepo;
    private ProductVariantRepository variantRepo;
    private StockAlertDelivery delivery;
    private StockAlertService service;
    private User alice;
    private List<Object> events;

    @BeforeEach
    void setUp() {
        alertRepo = mock(StockAlertRepository.class);
        userRepo = mock(UserRepository.class);
        variantRepo = mock(ProductVariantRepository.class);
        delivery = mock(StockAlertDelivery.class);
        service = new StockAlertService(alertRepo, userRepo, variantRepo, delivery);
        alice = Fx.user(7, "alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(alice));
        events = Fx.captureEvents();
    }

    @AfterEach
    void tearDown() {
        Fx.release();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static StockAlertResponse resp() {
        return new StockAlertResponse(1L, 1L, "Bike", "bike", 11L, "Red", 0, LocalDateTime.now(), null, true);
    }

    private static int status(Runnable r) {
        try {
            r.run();
        } catch (ResponseStatusException e) {
            return e.getStatusCode().value();
        }
        return 0;
    }

    // ---------------- subscribe ----------------
    @Test
    void anonymousAndMissingVariantRefused() {
        assertThat(status(() -> service.subscribe(null, 11L))).isEqualTo(401);
        assertThat(status(() -> service.subscribe("  ", 11L))).isEqualTo(401);
        assertThat(status(() -> service.subscribe("alice", null))).isEqualTo(400);
        assertThat(status(() -> service.list(null))).isEqualTo(401);
        when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThat(status(() -> service.subscribe("ghost", 11L))).isEqualTo(404);
        when(variantRepo.findStockOnSale(99L)).thenReturn(Optional.empty());
        assertThat(status(() -> service.subscribe("alice", 99L))).isEqualTo(404);
    }

    @Test
    void cannotWaitForColourThatIsInStock() {
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(3));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        assertThat(status(() -> service.subscribe("alice", 11L))).isEqualTo(409);
        verify(alertRepo, never()).save(any());
    }

    @Test
    void newSubscriptionIsCreated() {
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(0));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        when(variantRepo.getReferenceById(11L)).thenReturn(new ProductVariant());
        when(alertRepo.findResponseByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(resp()));
        StockAlertSubscription r = service.subscribe("alice", 11L);
        assertThat(r.created()).isTrue();
        ArgumentCaptor<StockAlert> cap = ArgumentCaptor.forClass(StockAlert.class);
        verify(alertRepo).save(cap.capture());
        assertThat(cap.getValue().getNotifiedAt()).isNull();
        assertThat(cap.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateSubscriptionKeepsOneRowAndIsNotCreated() {
        StockAlert existing = new StockAlert();
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(0));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(existing));
        when(alertRepo.findResponseByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(resp()));
        StockAlertSubscription r = service.subscribe("alice", 11L);
        assertThat(r.created()).isFalse();
        verify(alertRepo, never()).save(any());
    }

    @Test
    void waitingRowStaysEvenIfStockCameBackMeanwhile() {
        // already waiting: answered "not created" without a conflict, even when the colour is now in stock
        StockAlert existing = new StockAlert();
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(9));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(existing));
        when(alertRepo.findResponseByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(resp()));
        assertThat(service.subscribe("alice", 11L).created()).isFalse();
    }

    @Test
    void usedRowIsReArmed() {
        StockAlert used = new StockAlert();
        used.setNotifiedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(0));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(used));
        when(alertRepo.findResponseByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(resp()));
        assertThat(service.subscribe("alice", 11L).created()).isTrue();
        assertThat(used.getNotifiedAt()).isNull();
        verify(alertRepo).save(used);
    }

    @Test
    void usedRowCannotBeReArmedWhileInStock() {
        StockAlert used = new StockAlert();
        used.setNotifiedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(2));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(used));
        assertThat(status(() -> service.subscribe("alice", 11L))).isEqualTo(409);
        assertThat(used.getNotifiedAt()).isNotNull();
    }

    @Test
    void raceOnUniqueKeyAnswersExistingRowNotCreated() {
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(0));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        when(variantRepo.getReferenceById(11L)).thenReturn(new ProductVariant());
        when(alertRepo.save(any())).thenThrow(new DataIntegrityViolationException("dup"));
        when(alertRepo.findResponseByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(resp()));
        assertThat(service.subscribe("alice", 11L).created()).isFalse();
    }

    @Test
    void restockedMeanwhileTellsRightAway() {
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(0));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        when(variantRepo.getReferenceById(11L)).thenReturn(new ProductVariant());
        when(variantRepo.isOnSaleAndInStock(11L)).thenReturn(true);
        when(alertRepo.findResponseByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(resp()));
        service.subscribe("alice", 11L);
        verify(delivery).deliver(11L);
    }

    @Test
    void stillSoldOutDoesNotDeliver() {
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(0));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        when(variantRepo.getReferenceById(11L)).thenReturn(new ProductVariant());
        when(alertRepo.findResponseByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(resp()));
        service.subscribe("alice", 11L);
        verify(delivery, never()).deliver(anyLong());
    }

    @Test
    void deliveryFailureDoesNotBreakSubscribe() {
        when(variantRepo.findStockOnSale(11L)).thenReturn(Optional.of(0));
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty());
        when(variantRepo.getReferenceById(11L)).thenReturn(new ProductVariant());
        when(variantRepo.isOnSaleAndInStock(11L)).thenReturn(true);
        when(delivery.deliver(11L)).thenThrow(new IllegalStateException("smtp"));
        when(alertRepo.findResponseByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(resp()));
        assertThat(service.subscribe("alice", 11L).created()).isTrue();
    }

    @Test
    void unsubscribeDeletesOnlyOwnRowAndToleratesUnknown() {
        StockAlert row = new StockAlert();
        when(alertRepo.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.of(row));
        service.unsubscribe("alice", 11L);
        verify(alertRepo).delete(row);
        when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());
        service.unsubscribe("ghost", 11L);
        verify(alertRepo, times(1)).delete(any());
        assertThat(status(() -> service.unsubscribe(null, 11L))).isEqualTo(401);
    }

    // ---------------- notifyRestocked ----------------
    @Test
    void notifyRestockedIgnoresNullAndUnsavedVariants() {
        service.notifyRestocked(null);
        service.notifyRestocked(new ProductVariant());
        verify(delivery, never()).deliver(anyLong());
    }

    @Test
    void notifyRestockedWithoutTransactionDeliversImmediately() {
        ProductVariant v = new ProductVariant();
        v.setId(11L);
        service.notifyRestocked(v);
        verify(delivery).deliver(11L);
    }

    @Test
    void notifyRestockedInsideTransactionWaitsForCommit() {
        ProductVariant v = new ProductVariant();
        v.setId(11L);
        TransactionSynchronizationManager.initSynchronization();
        service.notifyRestocked(v);
        verify(delivery, never()).deliver(anyLong());
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.get(0).afterCommit();
        verify(delivery).deliver(11L);
    }

    @Test
    void notifyRestockedByIdSwallowsFailures() {
        when(delivery.deliver(11L)).thenThrow(new RuntimeException("boom"));
        assertThat(service.notifyRestockedById(11L)).isZero();
        when(delivery.deliver(12L)).thenReturn(4);
        assertThat(service.notifyRestockedById(12L)).isEqualTo(4);
    }

    // ---------------- sweep ----------------
    @Test
    void sweepContinuesAfterOneColourFails() {
        StockAlertService svc = mock(StockAlertService.class);
        when(svc.waitingVariantIdsInStock()).thenReturn(List.of(1L, 2L, 3L));
        when(svc.notifyRestockedById(1L)).thenReturn(2);
        when(svc.notifyRestockedById(2L)).thenThrow(new RuntimeException("x"));
        when(svc.notifyRestockedById(3L)).thenReturn(1);
        new StockAlertSweepJob(svc).sweep();
        verify(svc).notifyRestockedById(3L);
    }

    @Test
    void sweepSurvivesListFailure() {
        StockAlertService svc = mock(StockAlertService.class);
        when(svc.waitingVariantIdsInStock()).thenThrow(new RuntimeException("db down"));
        new StockAlertSweepJob(svc).sweep();
        verify(svc, never()).notifyRestockedById(anyLong());
    }

    // ---------------- delivery ----------------
    private StockAlertDelivery realDelivery(UserRepository users, StockAlertRepository alerts, CacheManager cm) {
        return new StockAlertDelivery(alerts, users, variantRepo, mock(EntityManager.class), cm);
    }

    private ProductVariant inStockVariant(int stock) {
        Product p = Fx.product(1, "City Bike", 5);
        ProductVariant v = Fx.variant(p, 11, "Red", stock, true);
        return v;
    }

    private static StockAlert alertRow(long id) {
        StockAlert a = new StockAlert();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    void deliveryTellsOnlyActiveCustomersAndMarksThem() {
        UserRepository users = mock(UserRepository.class);
        StockAlertRepository alerts = mock(StockAlertRepository.class);
        CacheManager cm = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(cm.getCache(any())).thenReturn(cache);
        ProductVariant v = inStockVariant(3);
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(alertRow(1), alertRow(2), alertRow(3), alertRow(4), alertRow(5)));
        when(alerts.findRecipients(anyCollection())).thenReturn(List.of(
            new StockAlertRecipient(1L, 100L, "ACTIVE", true),
            new StockAlertRecipient(2L, 101L, "ACTIVE", false),   // locked
            new StockAlertRecipient(3L, 102L, "DELETED", true),   // erased
            new StockAlertRecipient(4L, 103L, "ACTIVE", true),    // promoted to staff: row removed
            new StockAlertRecipient(5L, 104L, "ACTIVE", null)));  // null active == active
        when(users.findCustomerIds(anyCollection())).thenReturn(List.of(100L, 101L, 102L, 104L));
        int told = realDelivery(users, alerts, cm).deliver(v);
        assertThat(told).isEqualTo(2);
        assertThat(events).hasSize(2).allSatisfy(e -> assertThat(e).isInstanceOf(StockAlertTriggeredEvent.class));
        assertThat(events.stream().map(e -> ((StockAlertTriggeredEvent) e).userId()).toList()).containsExactlyInAnyOrder(100L, 104L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> marked = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(alerts).markNotified(marked.capture(), any());
        assertThat(marked.getValue()).containsExactlyInAnyOrder(1L, 5L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> deleted = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(alerts).deleteByIds(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(4L);
        verify(cache, times(2)).clear();
    }

    @Test
    void deliveryDoesNothingWhenNotOnSaleOrOutOfStock() {
        UserRepository users = mock(UserRepository.class);
        StockAlertRepository alerts = mock(StockAlertRepository.class);
        StockAlertDelivery d = realDelivery(users, alerts, mock(CacheManager.class));
        assertThat(d.deliver((ProductVariant) null)).isZero();
        assertThat(d.deliver(inStockVariant(0))).isZero();
        ProductVariant inactive = inStockVariant(5);
        inactive.setActive(false);
        assertThat(d.deliver(inactive)).isZero();
        ProductVariant bikeOff = inStockVariant(5);
        bikeOff.getProduct().setActive(false);
        assertThat(d.deliver(bikeOff)).isZero();
        ProductVariant noStock = inStockVariant(1);
        noStock.setStockQuantity(null);
        assertThat(d.deliver(noStock)).isZero();
        verify(alerts, never()).lockWaitingByVariantId(anyLong());
        assertThat(events).isEmpty();
    }

    @Test
    void deliveryWithNobodyWaitingTouchesNothing() {
        StockAlertRepository alerts = mock(StockAlertRepository.class);
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of());
        assertThat(realDelivery(mock(UserRepository.class), alerts, mock(CacheManager.class)).deliver(inStockVariant(2))).isZero();
        verify(alerts, never()).markNotified(any(), any());
        verify(alerts, never()).deleteByIds(any());
    }

    @Test
    void disabledAccountsKeepRowArmedAndDoNotEvictCache() {
        UserRepository users = mock(UserRepository.class);
        StockAlertRepository alerts = mock(StockAlertRepository.class);
        CacheManager cm = mock(CacheManager.class);
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(alertRow(1)));
        when(alerts.findRecipients(anyCollection())).thenReturn(List.of(new StockAlertRecipient(1L, 100L, "SUSPENDED", true)));
        when(users.findCustomerIds(anyCollection())).thenReturn(List.of(100L));
        assertThat(realDelivery(users, alerts, cm).deliver(inStockVariant(2))).isZero();
        verify(alerts, never()).markNotified(any(), any());
        verify(cm, never()).getCache(any());
        assertThat(events).isEmpty();
    }

    @Test
    void secondDeliveryTellsNobodyBecauseRowsAreUsed() {
        // the locking query only returns rows with notified_at null: a second restock sees none
        StockAlertRepository alerts = mock(StockAlertRepository.class);
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of());
        StockAlertDelivery d = realDelivery(mock(UserRepository.class), alerts, mock(CacheManager.class));
        assertThat(d.deliver(inStockVariant(2))).isZero();
        assertThat(d.deliver(inStockVariant(2))).isZero();
    }

    @Test
    void eventCarriesProductSlugAndColourFallback() {
        UserRepository users = mock(UserRepository.class);
        StockAlertRepository alerts = mock(StockAlertRepository.class);
        CacheManager cm = mock(CacheManager.class);
        ProductVariant v = inStockVariant(2);
        v.setColorName(null);
        v.setVariantName("Large");
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(alertRow(1)));
        when(alerts.findRecipients(anyCollection())).thenReturn(List.of(new StockAlertRecipient(1L, 100L, "ACTIVE", true)));
        when(users.findCustomerIds(anyCollection())).thenReturn(List.of(100L));
        realDelivery(users, alerts, cm).deliver(v);
        StockAlertTriggeredEvent e = (StockAlertTriggeredEvent) events.get(0);
        assertThat(e.colorName()).isEqualTo("Large");
        assertThat(e.productSlug()).isEqualTo("city-bike");
        assertThat(e.productName()).isEqualTo("City Bike");
    }
}
