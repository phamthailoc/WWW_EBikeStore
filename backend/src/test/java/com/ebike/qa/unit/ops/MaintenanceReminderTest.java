package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.MaintenanceDueEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.warranty.MaintenanceReminder;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.warranty.MaintenanceReminderRepository;
import com.ebike.service.warranty.MaintenanceReminderJob;
import com.ebike.service.warranty.MaintenanceReminderService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class MaintenanceReminderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 6, 1, 8, 0);
    private OrderItemUnitRepository units;
    private MaintenanceReminderRepository reminders;
    private PlatformTransactionManager tm;
    private List<Object> events;
    private User owner;

    @BeforeEach
    void setUp() {
        units = mock(OrderItemUnitRepository.class);
        reminders = mock(MaintenanceReminderRepository.class);
        tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        events = Fx.captureEvents();
        owner = Fx.user(7, "alice");
    }

    @AfterEach
    void tearDown() {
        Fx.release();
    }

    private MaintenanceReminderService svc(String intervals, int batch, int max) {
        return new MaintenanceReminderService(units, reminders, tm, intervals, batch, max);
    }

    private OrderItemUnit unit(long id, LocalDateTime delivered) {
        return Fx.unit(id, owner, delivered, 36, null);
    }

    private void feed(List<OrderItemUnit> list) {
        List<Long> ids = list.stream().map(OrderItemUnit::getId).toList();
        when(units.findDeliveredUnitIdsAfter(eq(0L), any(), any(Pageable.class))).thenReturn(ids);
        when(units.findDeliveredUnitIdsAfter(eq(ids.isEmpty() ? 0L : ids.get(ids.size() - 1)), any(), any(Pageable.class)))
            .thenReturn(ids.isEmpty() ? ids : List.of());
        when(units.findForWarrantyByIdIn(anyCollection())).thenReturn(list);
        when(reminders.findByUnitIdIn(anyCollection())).thenReturn(List.of());
    }

    @Test
    void nothingDeliveredNothingSent() {
        when(units.findDeliveredUnitIdsAfter(anyLong(), any(), any())).thenReturn(List.of());
        assertThat(svc("P1M,P6M", 10, 10).sendDueReminders(NOW)).isZero();
        verify(reminders, never()).saveAll(any());
    }

    @Test
    void notYetDueMeansNoReminder() {
        feed(List.of(unit(1, NOW.minusDays(10))));
        assertThat(svc("P1M,P6M", 10, 10).sendDueReminders(NOW)).isZero();
        assertThat(events).isEmpty();
    }

    @Test
    void dueExactlyAtTheInstantIsAnnounced() {
        feed(List.of(unit(1, NOW.minusMonths(1))));
        assertThat(svc("P1M,P6M", 10, 10).sendDueReminders(NOW)).isEqualTo(1);
        MaintenanceDueEvent e = (MaintenanceDueEvent) events.get(0);
        assertThat(e.userId()).isEqualTo(7L);
        assertThat(e.milestone()).isEqualTo("P1M");
        assertThat(e.frameNumber()).isEqualTo("FRAME1");
    }

    @Test
    void oneSecondBeforeDueNothing() {
        feed(List.of(unit(1, NOW.minusMonths(1).plusSeconds(1))));
        assertThat(svc("P1M,P6M", 10, 10).sendDueReminders(NOW)).isZero();
    }

    @Test
    void severalDueAtOnceAnnounceOnlyLatestAndStoreOlderAsPassedOver() {
        feed(List.of(unit(1, NOW.minusMonths(13))));
        assertThat(svc("P1M,P6M,P12M,P18M", 10, 10).sendDueReminders(NOW)).isEqualTo(1);
        assertThat(events).hasSize(1);
        assertThat(((MaintenanceDueEvent) events.get(0)).milestone()).isEqualTo("P1Y");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaintenanceReminder>> cap = ArgumentCaptor.forClass(List.class);
        verify(reminders).saveAll(cap.capture());
        assertThat(cap.getValue()).hasSize(3);
        assertThat(cap.getValue().stream().filter(MaintenanceReminder::isNotified)).hasSize(1);
        assertThat(cap.getValue().get(2).isNotified()).isTrue();
        assertThat(cap.getValue().get(0).isNotified()).isFalse();
    }

    @Test
    void alreadyHandledMilestonesAreNeverSentTwice() {
        OrderItemUnit u = unit(1, NOW.minusMonths(7));
        feed(List.of(u));
        MaintenanceReminder done1 = new MaintenanceReminder();
        done1.setUnit(u);
        done1.setMilestone("P1M");
        MaintenanceReminder done6 = new MaintenanceReminder();
        done6.setUnit(u);
        done6.setMilestone("P6M");
        when(reminders.findByUnitIdIn(anyCollection())).thenReturn(List.of(done1, done6));
        assertThat(svc("P1M,P6M", 10, 10).sendDueReminders(NOW)).isZero();
        verify(reminders, never()).saveAll(any());
    }

    @Test
    void runningTwiceDoesNotDuplicateWhenRowsPersisted() {
        OrderItemUnit u = unit(1, NOW.minusMonths(2));
        feed(List.of(u));
        List<MaintenanceReminder> stored = new ArrayList<>();
        when(reminders.saveAll(any())).thenAnswer(i -> {
            stored.addAll(i.getArgument(0));
            return i.getArgument(0);
        });
        MaintenanceReminderService s = svc("P1M,P6M", 10, 10);
        assertThat(s.sendDueReminders(NOW)).isEqualTo(1);
        when(reminders.findByUnitIdIn(anyCollection())).thenReturn(stored);
        assertThat(s.sendDueReminders(NOW)).isZero();
        assertThat(events).hasSize(1);
    }

    @Test
    void maxPerRunIsAHardCap() {
        List<OrderItemUnit> list = new ArrayList<>();
        for (long i = 1; i <= 5; i++) list.add(unit(i, NOW.minusMonths(2)));
        feed(list);
        assertThat(svc("P1M", 100, 3).sendDueReminders(NOW)).isEqualTo(3);
        assertThat(events).hasSize(3);
        // the vehicles left over were not stored, so tomorrow's run takes them
        verify(reminders, times(3)).saveAll(any());
    }

    @Test
    void zeroOrNegativeLimitsAreRaisedToOne() {
        List<OrderItemUnit> list = List.of(unit(1, NOW.minusMonths(2)), unit(2, NOW.minusMonths(2)));
        feed(list);
        assertThat(svc("P1M", 0, 0).sendDueReminders(NOW)).isEqualTo(1);
    }

    @Test
    void readWindowReachesBackOneLastIntervalPlus30Days() {
        feed(List.of());
        svc("P1M,P36M", 10, 10).sendDueReminders(NOW);
        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(units).findDeliveredUnitIdsAfter(eq(0L), since.capture(), any());
        assertThat(since.getValue()).isEqualTo(NOW.minusMonths(36).minusDays(30));
    }

    @Test
    void oneFailingVehicleDoesNotStopTheOthers() {
        OrderItemUnit bad = unit(1, NOW.minusMonths(2));
        OrderItemUnit good = unit(2, NOW.minusMonths(2));
        feed(List.of(bad, good));
        when(reminders.saveAll(any())).thenAnswer(i -> {
            List<MaintenanceReminder> rows = i.getArgument(0);
            if (rows.get(0).getUnit() == bad) throw new IllegalStateException("db");
            return rows;
        });
        assertThat(svc("P1M", 10, 10).sendDueReminders(NOW)).isEqualTo(1);
        assertThat(events).hasSize(1);
    }

    @Test
    void guestOrderStoresRowsButSendsNothing() {
        OrderItemUnit u = unit(1, NOW.minusMonths(2));
        u.getOrderItem().getOrder().setUser(null);
        feed(List.of(u));
        assertThat(svc("P1M", 10, 10).sendDueReminders(NOW)).isZero();
        assertThat(events).isEmpty();
    }

    @Test
    void unreadableBatchIsSkippedNotFatal() {
        when(units.findDeliveredUnitIdsAfter(eq(0L), any(), any())).thenReturn(List.of(1L));
        when(units.findForWarrantyByIdIn(anyCollection())).thenThrow(new IllegalStateException("db"));
        assertThat(svc("P1M", 1, 10).sendDueReminders(NOW)).isZero();
    }

    @Test
    void batchesAreWalkedByIdUntilShortPage() {
        OrderItemUnit a = unit(1, NOW.minusMonths(2));
        OrderItemUnit b = unit(2, NOW.minusMonths(2));
        when(units.findDeliveredUnitIdsAfter(eq(0L), any(), any())).thenReturn(List.of(1L));
        when(units.findDeliveredUnitIdsAfter(eq(1L), any(), any())).thenReturn(List.of(2L));
        when(units.findDeliveredUnitIdsAfter(eq(2L), any(), any())).thenReturn(List.of());
        when(units.findForWarrantyByIdIn(List.of(1L))).thenReturn(List.of(a));
        when(units.findForWarrantyByIdIn(List.of(2L))).thenReturn(List.of(b));
        when(reminders.findByUnitIdIn(anyCollection())).thenReturn(List.of());
        assertThat(svc("P1M", 1, 10).sendDueReminders(NOW)).isEqualTo(2);
    }

    @Test
    void jobSwallowsServiceFailure() {
        MaintenanceReminderService s = mock(MaintenanceReminderService.class);
        when(s.sendDueReminders(any())).thenThrow(new RuntimeException("x"));
        new MaintenanceReminderJob(s).sendReminders();
        MaintenanceReminderService s2 = mock(MaintenanceReminderService.class);
        when(s2.sendDueReminders(any())).thenReturn(3);
        new MaintenanceReminderJob(s2).sendReminders();
        verify(s2).sendDueReminders(any());
        assertThat(ReflectionTestUtils.getField(new MaintenanceReminderJob(s2), "service")).isSameAs(s2);
    }
}
