package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.warranty.WarrantyClaimResponse;
import com.ebike.dto.warranty.WarrantyClaimUpdateRequest;
import com.ebike.dto.warranty.WarrantyLookupResponse;
import com.ebike.dto.warranty.WarrantyUnitResponse;
import com.ebike.event.notification.WarrantyClaimStatusChangedEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.warranty.WarrantyClaim;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.warranty.WarrantyService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class WarrantyServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private OrderItemUnitRepository units;
    private WarrantyClaimRepository claims;
    private UserRepository users;
    private AuditLogService audit;
    private WarrantyService service;
    private final List<Object> events = new ArrayList<>();
    private User owner;

    private static Clock at(LocalDateTime t) {
        return Clock.fixed(t.atZone(ZONE).toInstant(), ZONE);
    }

    @BeforeEach
    void setUp() {
        units = mock(OrderItemUnitRepository.class);
        claims = mock(WarrantyClaimRepository.class);
        users = mock(UserRepository.class);
        audit = mock(AuditLogService.class);
        service = new WarrantyService(units, claims, users, audit, "P1M,P6M,P12M");
        setNow(LocalDateTime.of(2025, 6, 1, 12, 0));
        owner = Fx.user(7, "alice");
        when(users.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(claims.save(any(WarrantyClaim.class))).thenAnswer(i -> {
            WarrantyClaim c = i.getArgument(0);
            if (c.getId() == null) ReflectionTestUtils.setField(c, "id", 100L);
            if (c.getCreatedAt() == null) ReflectionTestUtils.setField(c, "createdAt", LocalDateTime.of(2025, 6, 1, 12, 0));
            if (c.getUpdatedAt() == null) ReflectionTestUtils.setField(c, "updatedAt", LocalDateTime.of(2025, 6, 1, 12, 0));
            return c;
        });
        NotificationEventBridge bridge = new NotificationEventBridge();
        bridge.setApplicationEventPublisher(new ApplicationEventPublisher() {
            @Override
            public void publishEvent(Object event) {
                events.add(event);
            }
        });
    }

    @AfterEach
    void tearDown() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
    }

    private void setNow(LocalDateTime t) {
        ReflectionTestUtils.setField(service, "clock", at(t));
    }

    private OrderItemUnit deliveredUnit(long id, LocalDateTime delivered, Integer pinned, Integer spec) {
        OrderItemUnit u = Fx.unit(id, owner, delivered, pinned, spec);
        when(units.findForWarrantyById(id)).thenReturn(Optional.of(u));
        when(units.findLockedById(id)).thenReturn(Optional.of(u));
        return u;
    }

    // ---------------- lookup ----------------
    @Test
    void lookupNormalisesFrameAndReportsDates() {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2024, 6, 1, 10, 0), 24, null);
        u.setFrameNumber("AB-12345");
        when(units.findForWarrantyByFrameNumber("AB-12345")).thenReturn(Optional.of(u));
        WarrantyLookupResponse r = service.lookup("  ab- 12345 ");
        assertThat(r.frameNumber()).isEqualTo("AB-12345");
        assertThat(r.warrantyStartsAt()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(r.warrantyEndsAt()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(r.coverage()).isEqualTo("ACTIVE");
        assertThat(r.warrantyMonths()).isEqualTo(24);
    }

    @Test
    void lookupRejectsBadFramesWith400() {
        for (String bad : new String[] {null, "", "   ", "ABCD", "A".repeat(31), "AB_1234", "AB 12'; DROP", "ÂB123456"}) {
            assertThatThrownBy(() -> service.lookup(bad)).as("frame=" + bad)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        verify(units, never()).findForWarrantyByFrameNumber(anyString());
    }

    @Test
    void lookupBoundaryLengths() {
        when(units.findForWarrantyByFrameNumber(anyString())).thenReturn(Optional.empty());
        for (String ok : new String[] {"ABCDE", "A".repeat(30)}) {
            assertThatThrownBy(() -> service.lookup(ok)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        }
    }

    @Test
    void lookupUnknownAndNotDeliveredLookIdentical() {
        when(units.findForWarrantyByFrameNumber("NOPE12345")).thenReturn(Optional.empty());
        OrderItemUnit pending = Fx.unit(2, owner, null, 12, null);
        pending.getOrderItem().getOrder().setStatus(OrderStatus.PENDING);
        pending.setFrameNumber("PEND12345");
        when(units.findForWarrantyByFrameNumber("PEND12345")).thenReturn(Optional.of(pending));
        ResponseStatusException unknown = catchRse(() -> service.lookup("NOPE12345"));
        ResponseStatusException notDelivered = catchRse(() -> service.lookup("PEND12345"));
        assertThat(notDelivered.getStatusCode()).isEqualTo(unknown.getStatusCode());
        assertThat(notDelivered.getReason()).isEqualTo(unknown.getReason());
    }

    @Test
    void lookupNeverLeaksOwner() {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        when(units.findForWarrantyByFrameNumber("FRAME1")).thenReturn(Optional.empty());
        u.setFrameNumber("FRAME1");
        when(units.findForWarrantyByFrameNumber("FRAME1")).thenReturn(Optional.of(u));
        assertThat(service.lookup("frame1").toString()).doesNotContain("alice").doesNotContain("example.com");
    }

    @Test
    void lookupLastDayStillActiveNextDayExpired() {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2024, 6, 1, 18, 0), 12, null);
        when(units.findForWarrantyByFrameNumber("FRAME1")).thenReturn(Optional.of(u));
        setNow(LocalDateTime.of(2025, 6, 1, 23, 59, 59));
        assertThat(service.lookup("FRAME1").coverage()).isEqualTo("ACTIVE");
        setNow(LocalDateTime.of(2025, 6, 2, 0, 0, 0));
        assertThat(service.lookup("FRAME1").coverage()).isEqualTo("EXPIRED");
    }

    @Test
    void lookupFallsBackToProductSpecMonthsWhenNotPinned() {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2025, 1, 1, 0, 0), null, 36);
        when(units.findForWarrantyByFrameNumber("FRAME1")).thenReturn(Optional.of(u));
        assertThat(service.lookup("FRAME1").warrantyMonths()).isEqualTo(36);
    }

    @Test
    void lookupUnknownWhenNoMonthsAnywhere() {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2025, 1, 1, 0, 0), null, null);
        when(units.findForWarrantyByFrameNumber("FRAME1")).thenReturn(Optional.of(u));
        WarrantyLookupResponse r = service.lookup("FRAME1");
        assertThat(r.coverage()).isEqualTo("UNKNOWN");
        assertThat(r.warrantyEndsAt()).isNull();
    }

    @Test
    void pinnedMonthsWinOverCurrentSpec() {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2025, 1, 1, 0, 0), 12, 60);
        assertThat(WarrantyService.warrantyMonths(u)).isEqualTo(12);
    }

    @Test
    void startsAtPriorityShipmentThenOrderDeliveredThenUpdatedAt() {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2025, 1, 2, 0, 0), 12, null);
        u.getOrderItem().getOrder().setShipment(Fx.shipment(LocalDateTime.of(2025, 1, 5, 8, 0)));
        assertThat(callStartsAt(u)).isEqualTo(LocalDateTime.of(2025, 1, 5, 8, 0));
        u.getOrderItem().getOrder().setShipment(Fx.shipment(null));
        assertThat(callStartsAt(u)).isEqualTo(LocalDateTime.of(2025, 1, 2, 0, 0));
        u.getOrderItem().getOrder().setDeliveredAt(null);
        assertThat(callStartsAt(u)).isEqualTo(LocalDateTime.of(2020, 1, 1, 0, 0));
        u.getOrderItem().getOrder().setStatus(OrderStatus.SHIPPED);
        assertThat(callStartsAt(u)).isNull();
    }

    private LocalDateTime callStartsAt(OrderItemUnit u) {
        return ReflectionTestUtils.invokeMethod(WarrantyService.class, "startsAt", u);
    }

    // ---------------- book ----------------
    @Test
    void bookRequiresUsername() {
        for (String bad : new String[] {null, "", "  "}) {
            assertThatThrownBy(() -> service.book(bad)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        }
        assertThatThrownBy(() -> service.claimsOf(null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void bookEmptyDoesNotQueryClaims() {
        when(units.findDeliveredByUsername("alice")).thenReturn(List.of());
        assertThat(service.book("alice")).isEmpty();
        verify(claims, never()).findByUnitIdIn(anyCollection());
    }

    @Test
    void bookShowsNewestClaimStatusAndTieBreaksById() {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        u.getOrderItem().getProduct().setSlug("bike-1");
        WarrantyClaim old = Fx.claim(1, u, owner, "DONE", null);
        WarrantyClaim sameSecondNewer = Fx.claim(2, u, owner, "NEW", null);
        when(units.findDeliveredByUsername("alice")).thenReturn(List.of(u));
        when(claims.findByUnitIdIn(anyCollection())).thenReturn(List.of(sameSecondNewer, old));
        List<WarrantyUnitResponse> book = service.book("alice");
        assertThat(book).hasSize(1);
        assertThat(book.get(0).claimStatus()).isEqualTo("NEW");
        assertThat(book.get(0).nextMaintenanceAt()).isEqualTo(LocalDate.of(2025, 7, 1)); // 2025-06-01 now: P1M ago due 2025-02-01.. P6M=2025-07-01
    }

    // ---------------- createClaim ----------------
    @Test
    void createClaimDescriptionBoundaries() {
        deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        for (String bad : new String[] {null, "", "123456789", "         9   ", "x".repeat(2001)}) {
            assertThatThrownBy(() -> service.createClaim("alice", 1L, bad)).as("desc=" + bad)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        assertThat(service.createClaim("alice", 1L, "1234567890").status()).isEqualTo("NEW");
        assertThat(service.createClaim("alice", 1L, "x".repeat(2000)).status()).isEqualTo("NEW");
    }

    @Test
    void createClaimRequiresLoginAndUnit() {
        assertThatThrownBy(() -> service.createClaim(null, 1L, "long enough text"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        assertThatThrownBy(() -> service.createClaim("alice", null, "long enough text"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        when(units.findLockedById(55L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createClaim("alice", 55L, "long enough text"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void createClaimOnSomeoneElsesBikeLooksLikeNotFound() {
        OrderItemUnit u = deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        u.getOrderItem().getOrder().setUser(Fx.user(8, "mallory"));
        assertThatThrownBy(() -> service.createClaim("alice", 1L, "long enough text"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        verify(claims, never()).save(any());
    }

    @Test
    void createClaimOnGuestOrderIsRefused() {
        OrderItemUnit u = deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        u.getOrderItem().getOrder().setUser(null);
        assertThatThrownBy(() -> service.createClaim("alice", 1L, "long enough text")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createClaimBeforeDeliveryRefused() {
        OrderItemUnit u = deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        u.getOrderItem().getOrder().setStatus(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.createClaim("alice", 1L, "long enough text"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void createClaimOnlyOneOpenPerUnit() {
        deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        when(claims.existsByUnitIdAndStatusIn(eq(1L), anyCollection())).thenReturn(true);
        assertThatThrownBy(() -> service.createClaim("alice", 1L, "long enough text"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        verify(claims, never()).save(any());
    }

    @Test
    void createClaimOpenSetCoversNewReceivedRepairingButNotClosed() {
        deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        service.createClaim("alice", 1L, "long enough text");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> cap = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(claims).existsByUnitIdAndStatusIn(eq(1L), cap.capture());
        assertThat(cap.getValue()).containsExactlyInAnyOrder("NEW", "RECEIVED", "REPAIRING");
    }

    @Test
    void createClaimRaceOnUniqueKeyBecomesConflict() {
        deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        when(claims.save(any())).thenThrow(new DataIntegrityViolationException("x",
            new RuntimeException("Duplicate entry for key 'uk_warranty_claims_open_unit'")));
        assertThatThrownBy(() -> service.createClaim("alice", 1L, "long enough text"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void createClaimOtherConstraintIsNotSwallowed() {
        deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        when(claims.save(any())).thenThrow(new DataIntegrityViolationException("x", new RuntimeException("fk_something")));
        assertThatThrownBy(() -> service.createClaim("alice", 1L, "long enough text")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createClaimOutOfWarrantyOnlyWhenExpiredNotUnknown() {
        // delivered 2024-06-01 18:00, 12 months: ends 2025-06-01 (covered through that day)
        deliveredUnit(1, LocalDateTime.of(2024, 6, 1, 18, 0), 12, null);
        setNow(LocalDateTime.of(2025, 6, 1, 23, 0));
        assertThat(service.createClaim("alice", 1L, "long enough text").outOfWarranty()).isFalse();
        setNow(LocalDateTime.of(2025, 6, 2, 0, 1));
        assertThat(service.createClaim("alice", 1L, "long enough text").outOfWarranty()).isTrue();
        deliveredUnit(2, LocalDateTime.of(2020, 1, 1, 0, 0), null, null);
        assertThat(service.createClaim("alice", 2L, "long enough text").outOfWarranty()).isFalse();
    }

    @Test
    void createClaimTakesLockBeforeAnyOtherRead() {
        deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        service.createClaim("alice", 1L, "long enough text");
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(units, claims);
        order.verify(units).findLockedById(1L);
        order.verify(units).findForWarrantyById(1L);
        order.verify(claims).existsByUnitIdAndStatusIn(eq(1L), anyCollection());
    }

    @Test
    void createClaimTrimsDescription() {
        deliveredUnit(1, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        WarrantyClaimResponse r = service.createClaim("alice", 1L, "   brake squeaks badly   ");
        assertThat(r.description()).isEqualTo("brake squeaks badly");
    }

    // ---------------- listClaims ----------------
    @Test
    void listClaimsFilterAndPaging() {
        when(claims.findByStatusIn(anyCollection(), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 1), false));
        when(claims.findAllBy(any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 1), false));
        when(claims.findByStatus(anyString(), any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 1), false));
        assertThatThrownBy(() -> service.listClaims("BOGUS", 0, 10)).isInstanceOf(ResponseStatusException.class);

        service.listClaims(null, -5, 100000);
        ArgumentCaptor<Pageable> p = ArgumentCaptor.forClass(Pageable.class);
        verify(claims).findByStatusIn(anyCollection(), p.capture());
        assertThat(p.getValue().getPageNumber()).isZero();
        assertThat(p.getValue().getPageSize()).isEqualTo(100);
        assertThat(p.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();

        service.listClaims("  all ", 0, 0);
        ArgumentCaptor<Pageable> p2 = ArgumentCaptor.forClass(Pageable.class);
        verify(claims).findAllBy(p2.capture());
        assertThat(p2.getValue().getPageSize()).isEqualTo(1);
        assertThat(p2.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();

        service.listClaims("done", 2, 10);
        ArgumentCaptor<Pageable> p3 = ArgumentCaptor.forClass(Pageable.class);
        verify(claims).findByStatus(eq("DONE"), p3.capture());
        assertThat(p3.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();

        service.listClaims("repairing", 0, 10);
        ArgumentCaptor<Pageable> p4 = ArgumentCaptor.forClass(Pageable.class);
        verify(claims).findByStatus(eq("REPAIRING"), p4.capture());
        assertThat(p4.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
    }

    // ---------------- updateClaim ----------------
    private WarrantyClaim claimIn(String status, String note) {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        WarrantyClaim c = Fx.claim(10, u, owner, status, note);
        when(claims.findLockedById(10L)).thenReturn(Optional.of(c));
        return c;
    }

    private ResponseStatusException update(String status, String note) {
        return catchRse(() -> service.updateClaim(10L, new WarrantyClaimUpdateRequest(status, note)));
    }

    @Test
    void updateUnknownClaim404() {
        when(claims.findLockedById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateClaim(99L, new WarrantyClaimUpdateRequest("RECEIVED", null)))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void updateMovesStatusNotifiesAndAudits() {
        claimIn("NEW", null);
        WarrantyClaimResponse r = service.updateClaim(10L, new WarrantyClaimUpdateRequest(" received ", null));
        assertThat(r.status()).isEqualTo("RECEIVED");
        verify(audit).record(anyString(), anyString());
        assertThat(events).hasSize(1);
        WarrantyClaimStatusChangedEvent e = (WarrantyClaimStatusChangedEvent) events.get(0);
        assertThat(e.userId()).isEqualTo(7L);
        assertThat(e.status()).isEqualTo("RECEIVED");
    }

    @Test
    void updateIllegalJumpsRefusedAndNothingSaved() {
        claimIn("NEW", null);
        assertThat(update("DONE", null).getStatusCode().value()).isEqualTo(400);
        assertThat(update("REPAIRING", null).getStatusCode().value()).isEqualTo(400);
        assertThat(update("NONSENSE", null).getStatusCode().value()).isEqualTo(400);
        verify(claims, never()).save(any());
        assertThat(events).isEmpty();
    }

    @Test
    void closedClaimIsFrozen() {
        claimIn("DONE", "ok");
        assertThat(update("RECEIVED", null).getStatusCode().value()).isEqualTo(400);
        assertThat(update(null, "new note").getStatusCode().value()).isEqualTo(400);
        assertThat(update("DONE", null).getStatusCode().value()).isEqualTo(400); // same-step on closed claim is not allowed
        verify(claims, never()).save(any());
    }

    @Test
    void rejectedClaimIsFrozenToo() {
        claimIn("REJECTED", "reason");
        assertThat(update("NEW", null).getStatusCode().value()).isEqualTo(400);
        assertThat(update(null, "x").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void rejectionNeedsFreshReason() {
        claimIn("NEW", null);
        assertThat(update("REJECTED", null).getStatusCode().value()).isEqualTo(400);
        assertThat(update("REJECTED", "   ").getStatusCode().value()).isEqualTo(400);
        claimIn("RECEIVED", "bring the bike in");
        assertThat(update("REJECTED", null).getStatusCode().value()).isEqualTo(400); // old note is not a reason
        assertThat(update("REJECTED", "bring the bike in").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void rejectionWithReasonWorks() {
        WarrantyClaim c = claimIn("NEW", null);
        WarrantyClaimResponse r = service.updateClaim(10L, new WarrantyClaimUpdateRequest("REJECTED", "  abuse  "));
        assertThat(r.status()).isEqualTo("REJECTED");
        assertThat(c.getStaffNote()).isEqualTo("abuse");
    }

    @Test
    void noteOnlyUpdateNotifiesAsNote() {
        claimIn("RECEIVED", null);
        service.updateClaim(10L, new WarrantyClaimUpdateRequest(null, "come Monday"));
        assertThat(events).hasSize(1);
        assertThat(((WarrantyClaimStatusChangedEvent) events.get(0)).status()).isEqualTo("NOTE");
    }

    @Test
    void sameNoteSameStatusIsNoOp() {
        claimIn("RECEIVED", "same");
        service.updateClaim(10L, new WarrantyClaimUpdateRequest("RECEIVED", "same"));
        verify(audit, never()).record(anyString(), anyString());
        assertThat(events).isEmpty();
    }

    @Test
    void clearingNoteDoesNotNotify() {
        WarrantyClaim c = claimIn("RECEIVED", "old");
        service.updateClaim(10L, new WarrantyClaimUpdateRequest(null, "   "));
        assertThat(c.getStaffNote()).isNull();
        assertThat(events).isEmpty();
        verify(audit).record(anyString(), anyString());
    }

    @Test
    void noteLengthBoundary() {
        claimIn("RECEIVED", null);
        assertThat(update(null, "x".repeat(3001)).getStatusCode().value()).isEqualTo(400);
        service.updateClaim(10L, new WarrantyClaimUpdateRequest(null, "x".repeat(3000)));
    }

    @Test
    void nullRequestIsHarmless() {
        claimIn("NEW", null);
        service.updateClaim(10L, null);
        assertThat(events).isEmpty();
    }

    @Test
    void blankStatusIgnored() {
        WarrantyClaim c = claimIn("NEW", null);
        service.updateClaim(10L, new WarrantyClaimUpdateRequest("  ", "note"));
        assertThat(c.getStatus()).isEqualTo("NEW");
    }

    @Test
    void fullHappyPathNewToDone() {
        WarrantyClaim c = claimIn("NEW", null);
        for (String s : new String[] {"RECEIVED", "REPAIRING", "DONE"}) {
            service.updateClaim(10L, new WarrantyClaimUpdateRequest(s, null));
            assertThat(c.getStatus()).isEqualTo(s);
        }
        assertThat(events).hasSize(3);
        verify(claims, org.mockito.Mockito.times(3)).save(c);
    }

    @Test
    void claimsOfMapsCustomerNameFallingBackToEmail() {
        OrderItemUnit u = Fx.unit(1, owner, LocalDateTime.of(2025, 1, 1, 0, 0), 12, null);
        WarrantyClaim c = Fx.claim(3, u, owner, "NEW", null);
        when(claims.findByUserUsernameOrderByCreatedAtDescIdDesc("alice")).thenReturn(List.of(c));
        assertThat(service.claimsOf("alice").get(0).customerName()).isEqualTo("alice@example.com");
        owner.setFirstName("Alice");
        owner.setLastName(null);
        assertThat(service.claimsOf("alice").get(0).customerName()).isEqualTo("Alice");
    }

    private static ResponseStatusException catchRse(Runnable r) {
        try {
            r.run();
        } catch (ResponseStatusException e) {
            return e;
        }
        throw new AssertionError("expected ResponseStatusException");
    }

    @SuppressWarnings("unused")
    private static Instant unused() {
        return Instant.EPOCH;
    }
}
