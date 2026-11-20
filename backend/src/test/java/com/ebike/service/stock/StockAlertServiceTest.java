package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.StockAlert;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.repository.product.StockAlertRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V22: one waiting row per customer and colour; restocking arms nobody twice. */
@ExtendWith(MockitoExtension.class)
class StockAlertServiceTest {
    @Mock StockAlertRepository alerts;
    @Mock UserRepository users;
    @Mock ProductVariantRepository variants;
    @Mock UserPreferenceRepository preferences;
    @Mock ApplicationEventPublisher publisher;
    StockAlertService service;
    User user;
    ProductVariant variant;

    @BeforeEach void setup() {
        service = new StockAlertService(alerts, users, variants, preferences);
        new NotificationEventBridge().setApplicationEventPublisher(publisher);
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
        org.mockito.Mockito.lenient().when(variants.findWithProductById(11L)).thenReturn(Optional.of(variant));
        org.mockito.Mockito.lenient().when(alerts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
        variant.setStockQuantity(3);
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

    private StockAlert waitingRow(long id) {
        StockAlert row = new StockAlert();
        ReflectionTestUtils.setField(row, "id", id);
        row.setUser(user);
        row.setVariant(variant);
        return row;
    }

    private StockAlertTriggeredEvent publishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        return (StockAlertTriggeredEvent) captor.getValue();
    }

    @Test void restockTellsEveryWaitingCustomerOnce() {
        variant.setStockQuantity(3);
        when(alerts.findByVariantIdAndNotifiedAtIsNull(11L)).thenReturn(List.of(waitingRow(1L)));
        when(alerts.claim(eq(1L), any())).thenReturn(1);
        assertEquals(1, service.notifyRestocked(variant));
        StockAlertTriggeredEvent event = publishedEvent();
        assertEquals(7L, event.userId());
        assertEquals("kinetic-x", event.productSlug());
        assertEquals("vi", event.language(), "no saved preference means Vietnamese");
    }

    @Test void restockUsesTheLanguageSavedInTheProfile() {
        variant.setStockQuantity(3);
        UserPreference preference = new UserPreference();
        preference.setLanguage("en");
        when(preferences.findByUserId(7L)).thenReturn(Optional.of(preference));
        when(alerts.findByVariantIdAndNotifiedAtIsNull(11L)).thenReturn(List.of(waitingRow(1L)));
        when(alerts.claim(eq(1L), any())).thenReturn(1);
        service.notifyRestocked(variant);
        assertEquals("en", publishedEvent().language());
    }

    @Test void aRowAlreadyClaimedByAnotherRequestIsNotNotifiedAgain() {
        variant.setStockQuantity(3);
        when(alerts.findByVariantIdAndNotifiedAtIsNull(11L)).thenReturn(List.of(waitingRow(1L)));
        when(alerts.claim(eq(1L), any())).thenReturn(0);
        assertEquals(0, service.notifyRestocked(variant));
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test void erasedAccountsAreSkipped() {
        variant.setStockQuantity(3);
        user.setAccountStatus("DELETED");
        when(alerts.findByVariantIdAndNotifiedAtIsNull(11L)).thenReturn(List.of(waitingRow(1L)));
        assertEquals(0, service.notifyRestocked(variant));
        verify(alerts, never()).claim(any(), any());
    }

    @Test void aColourWithNoStockNotifiesNobody() {
        variant.setStockQuantity(0);
        assertEquals(0, service.notifyRestocked(variant));
        verify(alerts, never()).findByVariantIdAndNotifiedAtIsNull(any());
    }

    @Test void sweepByIdNotifiesAColourPutBackOnSale() {
        variant.setStockQuantity(4);
        when(variants.findWithProductById(11L)).thenReturn(Optional.of(variant));
        when(alerts.findByVariantIdAndNotifiedAtIsNull(11L)).thenReturn(List.of(waitingRow(1L)));
        when(alerts.claim(eq(1L), any())).thenReturn(1);
        assertEquals(1, service.notifyRestockedById(11L));
    }

    @Test void aRaceOnTheUniqueKeyReturnsTheRowTheOtherRequestCreated() {
        StockAlert winner = new StockAlert();
        winner.setUser(user);
        winner.setVariant(variant);
        when(alerts.findByUserIdAndVariantId(7L, 11L)).thenReturn(Optional.empty(), Optional.of(winner));
        when(alerts.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        assertNotNull(service.subscribe("an", 11L));
    }

    @Test void restockOfARetiredColourNotifiesNobodyAndKeepsRowsArmed() {
        variant.setActive(false);
        assertEquals(0, service.notifyRestocked(variant));
        verify(alerts, never()).findByVariantIdAndNotifiedAtIsNull(any());
    }

    @Test void restockOfAnInactiveBikeNotifiesNobody() {
        variant.getProduct().setActive(false);
        assertEquals(0, service.notifyRestocked(variant));
        verify(alerts, never()).findByVariantIdAndNotifiedAtIsNull(any());
    }

    @Test void listHidesRetiredColoursAndInactiveBikes() {
        StockAlert live = new StockAlert();
        live.setUser(user);
        live.setVariant(variant);
        ProductVariant retired = new ProductVariant();
        retired.setId(12L);
        retired.setActive(false);
        retired.setProduct(variant.getProduct());
        StockAlert gone = new StockAlert();
        gone.setUser(user);
        gone.setVariant(retired);
        when(alerts.findByUserUsernameOrderByCreatedAtDesc("an")).thenReturn(List.of(live, gone));
        assertEquals(1, service.list("an").size());
    }
}
