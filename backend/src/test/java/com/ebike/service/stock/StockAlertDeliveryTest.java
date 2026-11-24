package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.StockAlert;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.repository.product.StockAlertRecipient;
import com.ebike.repository.product.StockAlertRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** V22: telling the waiting customers of a colour, in a fixed number of queries. */
@ExtendWith(MockitoExtension.class)
class StockAlertDeliveryTest {
    @Mock StockAlertRepository alerts;
    @Mock UserRepository users;
    @Mock ProductVariantRepository variants;
    @Mock ApplicationEventPublisher publisher;
    @Mock jakarta.persistence.EntityManager entityManager;
    @Mock org.springframework.cache.CacheManager cacheManager;
    @Mock org.springframework.cache.Cache cache;
    StockAlertDelivery delivery;
    User user;
    ProductVariant variant;

    @BeforeEach void setup() {
        delivery = new StockAlertDelivery(alerts, users, variants, entityManager, cacheManager);
        new NotificationEventBridge().setApplicationEventPublisher(publisher);
        user = new User();
        user.setId(7L);
        Product product = new Product();
        product.setId(1L);
        product.setActive(true);
        product.setName("Kinetic X");
        product.setSlug("kinetic-x");
        variant = new ProductVariant();
        variant.setId(11L);
        variant.setActive(true);
        variant.setColorName("Trắng");
        variant.setStockQuantity(3);
        variant.setProduct(product);
        // everyone in the test is a customer unless a test says otherwise
        lenient().when(users.findCustomerIds(any())).thenAnswer(invocation -> new java.util.ArrayList<Long>(invocation.<java.util.Collection<Long>>getArgument(0)));
    }

    /** The publisher is a static on the bridge: put it back so other test classes see an untouched state. */
    @AfterEach void resetBridge() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
    }

    private StockAlert waitingRow(long id, User owner) {
        StockAlert row = new StockAlert();
        ReflectionTestUtils.setField(row, "id", id);
        row.setUser(owner);
        row.setVariant(variant);
        return row;
    }

    private StockAlertTriggeredEvent publishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        return (StockAlertTriggeredEvent) captor.getValue();
    }


    /** The locked rows, and the join that gives their owners' account state and language. */
    private void waiting(StockAlert... rows) {
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(rows));
        lenient().when(alerts.findRecipients(any())).thenReturn(java.util.Arrays.stream(rows)
            .map(row -> new StockAlertRecipient(row.getId(), row.getUser().getId(), row.getUser().getAccountStatus(),
                row.getUser().getActive()))
            .toList());
    }

    @Test void restockTellsEveryWaitingCustomerOnce() {
        StockAlert row = waitingRow(1L, user);
        waiting(row);
        assertEquals(1, delivery.deliver(variant));
        StockAlertTriggeredEvent event = publishedEvent();
        assertEquals(7L, event.userId());
        assertEquals("kinetic-x", event.productSlug());
        verify(alerts).markNotified(eq(Set.of(1L)), any(LocalDateTime.class));
        verify(alerts, never()).deleteByIds(any());
    }

    @Test void tellingCustomersClearsTheProductPageCaches() {
        when(cacheManager.getCache(com.ebike.config.CacheNames.PRODUCT_LIST_CACHE)).thenReturn(cache);
        when(cacheManager.getCache(com.ebike.config.CacheNames.PRODUCT_DETAIL_CACHE)).thenReturn(cache);
        waiting(waitingRow(1L, user));
        delivery.deliver(variant);
        verify(cache, times(2)).clear();
    }

    @Test void tellingNobodyLeavesTheCachesAlone() {
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of());
        delivery.deliver(variant);
        verify(cacheManager, never()).getCache(any());
    }

    @Test void accountsAndLanguagesAreReadOncePerColourAndRowsMarkedWithOneStatement() {
        User other = new User();
        other.setId(8L);
        waiting(waitingRow(1L, user), waitingRow(2L, other));
        assertEquals(2, delivery.deliver(variant));
        verify(alerts, times(1)).findRecipients(any());
        verify(users, times(1)).findCustomerIds(any());
        verify(alerts, times(1)).markNotified(eq(Set.of(1L, 2L)), any(LocalDateTime.class));
        verify(alerts, never()).saveAll(any());
    }

    @Test void erasedAccountsAreSkippedAndStayUnmarked() {
        user.setAccountStatus("DELETED");
        waiting(waitingRow(1L, user));
        assertEquals(0, delivery.deliver(variant));
        verify(alerts, never()).markNotified(any(), any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test void accountsDisabledByAnAdminAreSkippedAndStayArmed() {
        user.setAccountStatus("SUSPENDED");
        user.setActive(false);
        waiting(waitingRow(1L, user));
        assertEquals(0, delivery.deliver(variant));
        verify(alerts, never()).markNotified(any(), any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test void aCustomerWhoBecameStaffLosesTheRowAndIsNotTold() {
        org.mockito.Mockito.doReturn(List.of()).when(users).findCustomerIds(any());
        waiting(waitingRow(1L, user));
        assertEquals(0, delivery.deliver(variant));
        verify(alerts).deleteByIds(Set.of(1L));
        verify(alerts, never()).markNotified(any(), any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test void aColourWithNoStockNotifiesNobody() {
        variant.setStockQuantity(0);
        assertEquals(0, delivery.deliver(variant));
        verify(alerts, never()).lockWaitingByVariantId(any());
    }

    @Test void restockOfARetiredColourNotifiesNobodyAndKeepsRowsArmed() {
        variant.setActive(false);
        assertEquals(0, delivery.deliver(variant));
        verify(alerts, never()).lockWaitingByVariantId(any());
    }

    @Test void restockOfAnInactiveBikeNotifiesNobody() {
        variant.getProduct().setActive(false);
        assertEquals(0, delivery.deliver(variant));
        verify(alerts, never()).lockWaitingByVariantId(any());
    }

    @Test void sweepByIdNotifiesAColourPutBackOnSale() {
        when(variants.findWithProductById(11L)).thenReturn(Optional.of(variant));
        waiting(waitingRow(1L, user));
        assertEquals(1, delivery.deliver(11L));
        verify(entityManager).refresh(variant);
    }
}
