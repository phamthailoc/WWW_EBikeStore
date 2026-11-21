package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.warranty.WarrantyClaim;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.admin.AuditLogService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
        when(units.findLockedByIdAndOwner(any(), any())).thenReturn(Optional.of(new OrderItemUnit()));
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
        when(claims.findLockedById(1L)).thenReturn(Optional.of(claim));
        return claim;
    }

    @Test
    void theNewestRequestOfASecondDecidesEvenWhenCreatedAtTies() {
        User me = user(1L, "me");
        OrderItemUnit unit = unitOf(me);
        unit.getOrderItem().setProduct(new Product());
        when(units.findDeliveredByUsername("me")).thenReturn(List.of(unit));
        LocalDateTime sameSecond = LocalDateTime.of(2026, 11, 20, 9, 0, 0);
        WarrantyClaim older = claimWith(10L, unit, WarrantyClaim.DONE, sameSecond);
        WarrantyClaim newer = claimWith(11L, unit, WarrantyClaim.NEW, sameSecond);
        // the repository gives them in no particular order
        when(claims.findByUnitIdIn(any())).thenReturn(List.of(newer, older));

        assertEquals(WarrantyClaim.NEW, service.book("me").get(0).claimStatus());
    }

    private static WarrantyClaim claimWith(long id, OrderItemUnit unit, String status, LocalDateTime createdAt) {
        WarrantyClaim claim = new WarrantyClaim();
        claim.setUnit(unit);
        claim.setStatus(status);
        ReflectionTestUtils.setField(claim, "id", id);
        ReflectionTestUtils.setField(claim, "createdAt", createdAt);
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
    void createClaimLocksOnlyTheCustomersOwnBike() {
        when(units.findLockedByIdAndOwner(7L, "me")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createClaim("me", 7L, "The motor makes a noise"));

        // a bike that is not theirs (or was deleted a moment ago) is a 404 before anything else is read
        assertEquals(404, error.getStatusCode().value());
        verify(users, never()).findByUsername(any());
        verify(units, never()).findForWarrantyById(any());
    }

    @Test
    void aSecondOpenRequestRejectedByTheDatabaseIsAConflict() {
        User me = user(1L, "me");
        when(users.findByUsername("me")).thenReturn(Optional.of(me));
        when(units.findForWarrantyById(7L)).thenReturn(Optional.of(unitOf(me)));
        when(claims.save(any(WarrantyClaim.class))).thenThrow(new DataIntegrityViolationException("uk_warranty_claims_open_unit"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createClaim("me", 7L, "The motor makes a noise"));

        assertEquals(409, error.getStatusCode().value());
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

        // the lock comes before any plain read, or the read snapshot would predate it (REPEATABLE READ)
        InOrder order = inOrder(units, users, claims);
        order.verify(units).findLockedByIdAndOwner(7L, "me");
        order.verify(users).findByUsername("me");
        order.verify(claims).existsByUnitIdAndStatusIn(any(), any());
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

    @Test
    void aBikeWithoutAWarrantyLengthIsNotMarkedAsOutOfWarranty() {
        User me = user(1L, "me");
        when(users.findByUsername("me")).thenReturn(Optional.of(me));
        when(units.findForWarrantyById(7L)).thenReturn(Optional.of(unitOf(me)));

        WarrantyClaimResponse created = service.createClaim("me", 7L, "The motor makes a noise");

        assertFalse(created.outOfWarranty());
    }

    @Test
    void theFullStaffQueueIsReadOnePageAtATimeNewestFirst() {
        when(claims.findAllBy(any(Pageable.class))).thenReturn(new SliceImpl<>(List.of()));
        service.listClaims("ALL", 2, 25);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(claims).findAllBy(page.capture());
        assertEquals(2, page.getValue().getPageNumber());
        assertEquals(25, page.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, page.getValue().getSort().getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC, page.getValue().getSort().getOrderFor("id").getDirection());
    }

    @Test
    void theDefaultQueueHoldsOpenRequestsOldestFirst() {
        when(claims.findByStatusIn(any(), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of()));
        service.listClaims(null, 0, 25);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(claims).findByStatusIn(org.mockito.ArgumentMatchers.eq(Set.of(WarrantyClaim.NEW, WarrantyClaim.RECEIVED, WarrantyClaim.REPAIRING)), page.capture());
        assertEquals(Sort.Direction.ASC, page.getValue().getSort().getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.ASC, page.getValue().getSort().getOrderFor("id").getDirection());
    }

    @Test
    void thePagerKnowsWhetherANextPageExists() {
        Pageable asked = PageRequest.of(0, 1);
        when(claims.findByStatus(any(), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(), asked, true));
        assertTrue(service.listClaims("DONE", 0, 1).hasNext());
        when(claims.findByStatus(any(), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(), asked, false));
        assertFalse(service.listClaims("DONE", 0, 1).hasNext());
    }

    @Test
    void aStatusFilterIsPagedToo() {
        when(claims.findByStatus(any(), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of()));
        service.listClaims("new", 0, 10);
        verify(claims).findByStatus(org.mockito.ArgumentMatchers.eq("NEW"), any(Pageable.class));
    }

    @Test
    void anOddPageOrSizeIsHeldInRange() {
        when(claims.findAllBy(any(Pageable.class))).thenReturn(new SliceImpl<>(List.of()));
        service.listClaims("ALL", -5, 100000);
        service.listClaims("ALL", 0, 0);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(claims, times(2)).findAllBy(page.capture());
        assertEquals(0, page.getAllValues().get(0).getPageNumber());
        assertEquals(WarrantyService.MAX_PAGE_SIZE, page.getAllValues().get(0).getPageSize());
        assertEquals(1, page.getAllValues().get(1).getPageSize());
    }

    @Test
    void errorsAreEnglishForAnEnglishRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en-US");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.lookup("ab"));
            assertEquals("A frame number has 5 to 30 letters, digits or hyphens.", error.getReason());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void errorsAreVietnameseByDefault() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.lookup("ab"));
        assertEquals("Số khung gồm 5 đến 30 ký tự chữ, số hoặc dấu gạch ngang.", error.getReason());
    }
}
