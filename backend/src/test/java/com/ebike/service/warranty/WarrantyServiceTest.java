package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.warranty.WarrantyClaimResponse;
import com.ebike.dto.warranty.WarrantyClaimUpdateRequest;
import com.ebike.event.notification.WarrantyClaimStatusChangedEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.warranty.WarrantyClaim;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.admin.AuditLogService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V20: who may open a warranty request, and how the staff move it along. */
class WarrantyServiceTest {

    private OrderItemUnitRepository units;
    private WarrantyClaimRepository claims;
    private UserRepository users;
    private WarrantyService service;
    private final List<Object> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        units = mock(OrderItemUnitRepository.class);
        claims = mock(WarrantyClaimRepository.class);
        users = mock(UserRepository.class);
        service = new WarrantyService(units, claims, users, mock(AuditLogService.class), WarrantyRules.DEFAULT_INTERVALS, Clock.systemDefaultZone());
        ApplicationEventPublisher publisher = events::add;
        new NotificationEventBridge().setApplicationEventPublisher(publisher);
        when(claims.save(any(WarrantyClaim.class))).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
    }

    private static User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }

    private static OrderItemUnit unitOf(User owner) {
        Order order = new Order();
        order.setStatus(OrderStatus.DELIVERED);
        order.setUser(owner);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductName("Test bike");
        OrderItemUnit unit = new OrderItemUnit();
        unit.setOrderItem(item);
        unit.setFrameNumber("FRAME12345");
        ReflectionTestUtils.setField(unit, "id", 7L);
        return unit;
    }

    private WarrantyClaim claimIn(String status, User owner) {
        WarrantyClaim claim = new WarrantyClaim();
        claim.setUnit(unitOf(owner));
        claim.setUser(owner);
        claim.setStatus(status);
        when(claims.findById(1L)).thenReturn(Optional.of(claim));
        return claim;
    }

    @Test
    void createClaimOnSomeoneElsesBikeIsNotFound() {
        User me = user(1L, "me");
        when(users.findByUsername("me")).thenReturn(Optional.of(me));
        when(units.findForWarrantyById(7L)).thenReturn(Optional.of(unitOf(user(2L, "other"))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createClaim("me", 7L, "The motor makes a noise"));

        assertEquals(404, error.getStatusCode().value());
        verify(claims, never()).save(any());
    }

    @Test
    void createClaimIsRefusedWhileAnotherOneIsOpen() {
        User me = user(1L, "me");
        when(users.findByUsername("me")).thenReturn(Optional.of(me));
        when(units.findForWarrantyById(7L)).thenReturn(Optional.of(unitOf(me)));
        when(claims.existsByUnitIdAndStatusIn(any(), any())).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createClaim("me", 7L, "The motor makes a noise"));

        assertEquals(409, error.getStatusCode().value());
        verify(claims, never()).save(any());
    }

    @Test
    void createClaimLocksTheBikeBeforeChecking() {
        User me = user(1L, "me");
        when(users.findByUsername("me")).thenReturn(Optional.of(me));
        when(units.findForWarrantyById(7L)).thenReturn(Optional.of(unitOf(me)));

        WarrantyClaimResponse created = service.createClaim("me", 7L, "The motor makes a noise");

        verify(units).findLockedById(7L);
        assertEquals(WarrantyClaim.NEW, created.status());
    }

    @Test
    void anInvalidStepIsRefused() {
        claimIn(WarrantyClaim.NEW, user(1L, "me"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.updateClaim(1L, new WarrantyClaimUpdateRequest("DONE", null)));

        assertEquals(400, error.getStatusCode().value());
        verify(claims, never()).save(any());
    }

    @Test
    void movingToANewStepNotifiesTheCustomer() {
        claimIn(WarrantyClaim.NEW, user(1L, "me"));

        WarrantyClaimResponse updated = service.updateClaim(1L, new WarrantyClaimUpdateRequest("RECEIVED", null));

        assertEquals(WarrantyClaim.RECEIVED, updated.status());
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof WarrantyClaimStatusChangedEvent);
    }

    @Test
    void savingOnlyANoteSendsNoEvent() {
        claimIn(WarrantyClaim.RECEIVED, user(1L, "me"));

        service.updateClaim(1L, new WarrantyClaimUpdateRequest(null, "Waiting for a part"));

        assertTrue(events.isEmpty());
    }

    @Test
    void rejectingNeedsAReason() {
        claimIn(WarrantyClaim.RECEIVED, user(1L, "me"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.updateClaim(1L, new WarrantyClaimUpdateRequest("REJECTED", "  ")));

        assertEquals(400, error.getStatusCode().value());
        verify(claims, never()).save(any());
    }

    @Test
    void rejectingWithAReasonWorks() {
        claimIn(WarrantyClaim.RECEIVED, user(1L, "me"));

        WarrantyClaimResponse updated = service.updateClaim(1L, new WarrantyClaimUpdateRequest("REJECTED", "Water damage"));

        assertEquals(WarrantyClaim.REJECTED, updated.status());
    }

    @Test
    void aClosedRequestKeepsItsNote() {
        claimIn(WarrantyClaim.DONE, user(1L, "me"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.updateClaim(1L, new WarrantyClaimUpdateRequest(null, "Changed later")));

        assertEquals(400, error.getStatusCode().value());
        verify(claims, never()).save(any());
    }

    @Test
    void lookupOfABikeNotYetDeliveredIsNotFound() {
        OrderItemUnit unit = unitOf(user(1L, "me"));
        unit.getOrderItem().getOrder().setStatus(OrderStatus.PROCESSING);
        when(units.findForWarrantyByFrameNumber("FRAME12345")).thenReturn(Optional.of(unit));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.lookup("frame12345"));

        assertEquals(404, error.getStatusCode().value());
    }
}
