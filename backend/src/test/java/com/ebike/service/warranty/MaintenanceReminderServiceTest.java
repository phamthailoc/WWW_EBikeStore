package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
        service = new MaintenanceReminderService(units, reminders, "P1M,P6M,P12M");
        ApplicationEventPublisher publisher = events::add;
        new NotificationEventBridge().setApplicationEventPublisher(publisher);
    }

    @AfterEach
    void tearDown() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
    }

    private static OrderItemUnit deliveredAt(LocalDateTime deliveredAt) {
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
        unit.setFrameNumber("FRAME12345");
        ReflectionTestUtils.setField(unit, "id", 7L);
        return unit;
    }

    @Test
    @SuppressWarnings("unchecked")
    void severalDueMilestonesAnnounceOnlyTheLatest() {
        when(units.findAllDeliveredForWarranty()).thenReturn(List.of(deliveredAt(NOW.minusMonths(7))));
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
        when(units.findAllDeliveredForWarranty()).thenReturn(List.of(unit));
        when(reminders.findByUnitIdIn(any())).thenReturn(List.of(done));

        int announced = service.sendDueReminders(NOW);

        assertEquals(0, announced);
        assertEquals(0, events.size());
        verify(reminders).saveAll(List.of());
    }

    @Test
    void aBikeNotDueYetIsLeftAlone() {
        when(units.findAllDeliveredForWarranty()).thenReturn(List.of(deliveredAt(NOW.minusDays(5))));
        when(reminders.findByUnitIdIn(any())).thenReturn(List.of());

        assertEquals(0, service.sendDueReminders(NOW));
        assertEquals(0, events.size());
    }

    @Test
    void noDeliveredBikesMeansNothingToDo() {
        when(units.findAllDeliveredForWarranty()).thenReturn(List.of());

        assertEquals(0, service.sendDueReminders(NOW));
        verify(reminders, never()).saveAll(any());
    }
}
