package com.ebike.service.warranty;

import com.ebike.event.notification.MaintenanceDueEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.warranty.MaintenanceReminder;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.warranty.MaintenanceReminderRepository;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V20: tells customers when a delivered vehicle reaches a maintenance milestone ({@code app.maintenance.intervals}, counted from
 * the delivery). Every handled milestone is stored, so a reminder is never sent twice. When several milestones are due at once
 * (the job was off, or the feature was just switched on for old orders) only the latest is announced; the older ones are
 * stored as passed over.
 */
@Service
public class MaintenanceReminderService {

    private final OrderItemUnitRepository units;
    private final MaintenanceReminderRepository reminders;
    private final List<Period> intervals;

    public MaintenanceReminderService(
        OrderItemUnitRepository units,
        MaintenanceReminderRepository reminders,
        @Value("${app.maintenance.intervals:" + WarrantyRules.DEFAULT_INTERVALS + "}") String intervals
    ) {
        this.units = units;
        this.reminders = reminders;
        this.intervals = WarrantyRules.parseIntervals(intervals);
    }

    /** Returns how many reminders were announced. */
    @Transactional
    public int sendDueReminders(LocalDateTime now) {
        List<OrderItemUnit> delivered = units.findAllDeliveredForWarranty();
        if (delivered.isEmpty()) {
            return 0;
        }
        Map<Long, Set<String>> handled = reminders.findByUnitIdIn(delivered.stream().map(OrderItemUnit::getId).toList()).stream()
            .collect(Collectors.groupingBy(reminder -> reminder.getUnit().getId(),
                Collectors.mapping(MaintenanceReminder::getMilestone, Collectors.toSet())));
        List<MaintenanceReminder> toSave = new ArrayList<>();
        int announced = 0;
        for (OrderItemUnit unit : delivered) {
            LocalDateTime starts = WarrantyService.startsAt(unit);
            Set<String> done = handled.getOrDefault(unit.getId(), new HashSet<>());
            List<Period> missing = WarrantyRules.dueMilestones(starts, intervals, now).stream()
                .filter(period -> !done.contains(period.toString()))
                .toList();
            if (missing.isEmpty()) {
                continue;
            }
            Period latest = missing.get(missing.size() - 1);
            for (Period period : missing) {
                MaintenanceReminder row = new MaintenanceReminder();
                row.setUnit(unit);
                row.setMilestone(period.toString());
                row.setDueAt(WarrantyRules.dueAt(starts, period));
                row.setNotified(period.equals(latest));
                toSave.add(row);
            }
            User owner = unit.getOrderItem().getOrder().getUser();
            if (owner != null) {
                NotificationEventBridge.publish(new MaintenanceDueEvent(
                    owner.getId(), unit.getOrderItem().getProductName(), unit.getFrameNumber(), latest.toString()));
                announced++;
            }
        }
        reminders.saveAll(toSave);
        return announced;
    }
}
