package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.repository.product.StockAlertRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import java.util.List;
import java.util.Optional;
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
    @Mock UserPreferenceRepository preferences;
    @Mock ApplicationEventPublisher publisher;
    StockAlertDelivery delivery;
    User user;
    ProductVariant variant;

    @BeforeEach void setup() {
        delivery = new StockAlertDelivery(alerts, users, variants, preferences);
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
        lenient().when(users.findAllById(any())).thenReturn(List.of(user));
        lenient().when(preferences.findByUserIdIn(any())).thenReturn(List.of());
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

    @Test void restockTellsEveryWaitingCustomerOnce() {
        StockAlert row = waitingRow(1L, user);
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(row));
        assertEquals(1, delivery.deliver(variant));
        StockAlertTriggeredEvent event = publishedEvent();
        assertEquals(7L, event.userId());
        assertEquals("kinetic-x", event.productSlug());
        assertEquals("vi", event.language(), "no saved preference means Vietnamese");
        assertNotNull(row.getNotifiedAt(), "the row is marked as used");
        verify(alerts).saveAll(List.of(row));
    }

    @Test void restockUsesTheLanguageSavedInTheProfile() {
        UserPreference preference = new UserPreference();
        preference.setUser(user);
        preference.setLanguage("en");
        when(preferences.findByUserIdIn(any())).thenReturn(List.of(preference));
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(waitingRow(1L, user)));
        delivery.deliver(variant);
        assertEquals("en", publishedEvent().language());
    }

    @Test void accountsAndPreferencesAreReadOncePerColourNotOncePerCustomer() {
        User other = new User();
        other.setId(8L);
        when(users.findAllById(any())).thenReturn(List.of(user, other));
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(waitingRow(1L, user), waitingRow(2L, other)));
        assertEquals(2, delivery.deliver(variant));
        verify(users, times(1)).findAllById(any());
        verify(preferences, times(1)).findByUserIdIn(any());
        verify(preferences, never()).findByUserId(any());
    }

    @Test void erasedAccountsAreSkippedAndStayUnmarked() {
        user.setAccountStatus("DELETED");
        StockAlert row = waitingRow(1L, user);
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(row));
        assertEquals(0, delivery.deliver(variant));
        assertNull(row.getNotifiedAt());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test void accountsDisabledByAnAdminAreSkippedAndStayArmed() {
        user.setAccountStatus("SUSPENDED");
        user.setActive(false);
        StockAlert row = waitingRow(1L, user);
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(row));
        assertEquals(0, delivery.deliver(variant));
        assertNull(row.getNotifiedAt());
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
        when(alerts.lockWaitingByVariantId(11L)).thenReturn(List.of(waitingRow(1L, user)));
        assertEquals(1, delivery.deliver(11L));
    }
}
