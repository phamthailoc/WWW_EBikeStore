package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.warranty.MaintenanceReminder;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.warranty.MaintenanceReminderRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

/** V20: a maintenance reminder is announced once; milestones that were skipped are stored as passed over. */
class MaintenanceReminderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 8, 0);

    private OrderItemUnitRepository units;
    private MaintenanceReminderRepository reminders;
    private MaintenanceReminderService service;
    private final List<Object> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        units = mock(OrderItemUnitRepository.class);
        reminders = mock(MaintenanceReminderRepository.class);
        service = new MaintenanceReminderService(units, reminders, mock(PlatformTransactionManager.class), "P1M,P6M,P12M", 2);
        // by default the first page holds the given vehicles and the next page is empty
        when(units.findDeliveredUnitIdsAfter(anyLong(), any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of());
        ApplicationEventPublisher publisher = events::add;
        new NotificationEventBridge().setApplicationEventPublisher(publisher);
    }

    @AfterEach
    void tearDown() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
    }

    private static OrderItemUnit deliveredAt(LocalDateTime deliveredAt) {
        return deliveredAt(deliveredAt, 7L);
    }

    private static OrderItemUnit deliveredAt(LocalDateTime deliveredAt, long id) {
        User owner = new User();
        owner.setId(1L);
        Shipment shipment = new Shipment();
        shipment.setDeliveredAt(deliveredAt);
        Order order = new Order();
        order.setStatus(OrderStatus.DELIVERED);
        order.setUser(owner);
        order.setShipment(shipment);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductName("Test bike");
        OrderItemUnit unit = new OrderItemUnit();
        unit.setOrderItem(item);
        unit.setFrameNumber("FRAME" + id);
        ReflectionTestUtils.setField(unit, "id", id);
        return unit;
    }

    /** One page that holds all the given vehicles (fewer than the batch size, so the run ends after it). */
    private void serve(OrderItemUnit... delivered) {
        List<Long> ids = java.util.Arrays.stream(delivered).map(OrderItemUnit::getId).toList();
        when(units.findDeliveredUnitIdsAfter(eq(0L), any(LocalDateTime.class), any(Pageable.class))).thenReturn(ids);
        when(units.findForWarrantyByIdIn(ids)).thenReturn(List.of(delivered));
    }

    @Test
    void theReadWindowReachesPastTheLastMilestoneSoItIsNotMissed() {
        service.sendDueReminders(NOW);

        // P1M,P6M,P12M: a bike handed over 12 months ago at 10:00 is due only after the 08:00 run of that day, so the window is wider
        LocalDateTime since = NOW.minusMonths(12).minusDays(MaintenanceReminderService.CATCH_UP_DAYS);
        verify(units).findDeliveredUnitIdsAfter(eq(0L), eq(since), any(Pageable.class));
        assertTrue(!since.isAfter(NOW.minusMonths(12).minusDays(1).withHour(10)));
    }

    @Test
    void aBikeDeliveredAtTenTwelveMonthsAgoIsRemindedOnTheNextMorningRun() {
        LocalDateTime delivered = NOW.minusDays(1).minusMonths(12).withHour(10);
        serve(deliveredAt(delivered));

        assertEquals(1, service.sendDueReminders(NOW));
    }

    @Test
    @SuppressWarnings("unchecked")
    void severalDueMilestonesAnnounceOnlyTheLatest() {
        serve(deliveredAt(NOW.minusMonths(7)));
        when(reminders.findByUnitIdIn(any())).thenReturn(List.of());

        int announced = service.sendDueReminders(NOW);

        assertEquals(1, announced);
        assertEquals(1, events.size());
        ArgumentCaptor<List<MaintenanceReminder>> saved = ArgumentCaptor.forClass(List.class);
        verify(reminders).saveAll(saved.capture());
        List<MaintenanceReminder> rows = saved.getValue();
        assertEquals(2, rows.size());
        assertEquals("P1M", rows.get(0).getMilestone());
        assertEquals(false, rows.get(0).isNotified());
        assertEquals("P6M", rows.get(1).getMilestone());
        assertEquals(true, rows.get(1).isNotified());
    }

    @Test
    void aHandledMilestoneIsNotAnnouncedAgain() {
        OrderItemUnit unit = deliveredAt(NOW.minusMonths(2));
        MaintenanceReminder done = new MaintenanceReminder();
        done.setUnit(unit);
        done.setMilestone("P1M");
        serve(unit);
        when(reminders.findByUnitIdIn(any())).thenReturn(List.of(done));

        int announced = service.sendDueReminders(NOW);

        assertEquals(0, announced);
        assertEquals(0, events.size());
        verify(reminders, never()).saveAll(any());
    }

    @Test
    void aBikeNotDueYetIsLeftAlone() {
        serve(deliveredAt(NOW.minusDays(5)));
        when(reminders.findByUnitIdIn(any())).thenReturn(List.of());

        assertEquals(0, service.sendDueReminders(NOW));
        assertEquals(0, events.size());
        verify(reminders, never()).saveAll(any());
    }

    @Test
    void noDeliveredBikesMeansNothingToDo() {
        assertEquals(0, service.sendDueReminders(NOW));
        verify(reminders, never()).saveAll(any());
        verify(units, never()).findForWarrantyByIdIn(any());
    }

    @Test
    void vehiclesAreReadPageByPageUntilAShortPage() {
        OrderItemUnit a = deliveredAt(NOW.minusMonths(2), 1L);
        OrderItemUnit b = deliveredAt(NOW.minusMonths(2), 2L);
        OrderItemUnit c = deliveredAt(NOW.minusMonths(2), 3L);
        when(units.findDeliveredUnitIdsAfter(eq(0L), any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        when(units.findDeliveredUnitIdsAfter(eq(2L), any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(3L));
        when(units.findForWarrantyByIdIn(List.of(1L, 2L))).thenReturn(List.of(a, b));
        when(units.findForWarrantyByIdIn(List.of(3L))).thenReturn(List.of(c));
        when(reminders.findByUnitIdIn(any())).thenReturn(List.of());

        assertEquals(3, service.sendDueReminders(NOW));
        assertEquals(3, events.size());
        verify(reminders, org.mockito.Mockito.times(3)).saveAll(any());
    }

    @Test
    void oneVehicleThatFailsDoesNotStopTheOthers() {
        OrderItemUnit bad = deliveredAt(NOW.minusMonths(2), 1L);
        OrderItemUnit good = deliveredAt(NOW.minusMonths(2), 2L);
        serve(bad, good);
        when(reminders.findByUnitIdIn(any())).thenReturn(List.of());
        when(reminders.saveAll(any())).thenThrow(new IllegalStateException("duplicate")).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(1, service.sendDueReminders(NOW));
        assertEquals(1, events.size());
    }

    @Test
    void aBatchThatCannotBeReadIsSkippedAndTheRunGoesOn() {
        when(units.findDeliveredUnitIdsAfter(eq(0L), any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        when(units.findDeliveredUnitIdsAfter(eq(2L), any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(3L));
        when(units.findForWarrantyByIdIn(List.of(1L, 2L))).thenThrow(new IllegalStateException("db down"));
        when(units.findForWarrantyByIdIn(List.of(3L))).thenReturn(List.of(deliveredAt(NOW.minusMonths(2), 3L)));
        when(reminders.findByUnitIdIn(any())).thenReturn(List.of());

        assertEquals(1, service.sendDueReminders(NOW));
    }
}
