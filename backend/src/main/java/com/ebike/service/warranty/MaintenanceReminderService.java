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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V20: tells customers when a delivered vehicle reaches a maintenance milestone ({@code app.maintenance.intervals}, counted from
 * the delivery). Every handled milestone is stored, so a reminder is never sent twice. When several milestones are due at once
 * (the job was off, or the feature was just switched on for old orders) only the latest is announced; the older ones are
 * stored as passed over.
 *
 * <p>The run reads the vehicles in batches (by id, so memory stays flat however many bikes were sold) and handles each vehicle
 * in its own transaction: a vehicle that fails is logged and left for the next run, the others are not affected, and its
 * notification and email are sent only after its own rows were saved.
 */
@Service
public class MaintenanceReminderService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceReminderService.class);
    static final int DEFAULT_BATCH_SIZE = 100;
    /** How long after the last milestone a vehicle is still read, so that milestone is not missed (see {@link #sendDueReminders}). */
    static final int CATCH_UP_DAYS = 30;

    private final OrderItemUnitRepository units;
    private final MaintenanceReminderRepository reminders;
    private final List<Period> intervals;
    private final TransactionTemplate perVehicle;
    private final int batchSize;

    @Autowired
    public MaintenanceReminderService(
        OrderItemUnitRepository units,
        MaintenanceReminderRepository reminders,
        PlatformTransactionManager transactionManager,
        @Value("${app.maintenance.intervals:" + WarrantyRules.DEFAULT_INTERVALS + "}") String intervals,
        @Value("${app.maintenance.batch-size:" + DEFAULT_BATCH_SIZE + "}") int batchSize
    ) {
        this.units = units;
        this.reminders = reminders;
        this.intervals = WarrantyRules.parseIntervals(intervals);
        this.perVehicle = new TransactionTemplate(transactionManager);
        this.perVehicle.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = Math.max(1, batchSize);
    }

    /** Returns how many reminders were announced. Not transactional itself: each vehicle commits on its own. */
    public int sendDueReminders(LocalDateTime now) {
        int announced = 0;
        long afterId = 0;
        // a vehicle handed over long before the last milestone has nothing left to remind: leave it out of the read. The window is
        // wider than the last interval, so the last milestone is still caught on the day it falls due (the hand-over may have been
        // later in the day than the job) and after the job was off for some days; a vehicle already handled is skipped by its stored rows.
        LocalDateTime since = now.minus(intervals.get(intervals.size() - 1)).minusDays(CATCH_UP_DAYS);
        while (true) {
            List<Long> ids = units.findDeliveredUnitIdsAfter(afterId, since, PageRequest.of(0, batchSize));
            if (ids.isEmpty()) {
                return announced;
            }
            afterId = ids.get(ids.size() - 1);
            announced += handleBatch(ids, now);
            if (ids.size() < batchSize) {
                return announced;
            }
        }
    }

    private int handleBatch(List<Long> ids, LocalDateTime now) {
        List<OrderItemUnit> batch;
        Map<Long, Set<String>> handled;
        try {
            batch = units.findForWarrantyByIdIn(ids);
            handled = reminders.findByUnitIdIn(ids).stream()
                .collect(Collectors.groupingBy(reminder -> reminder.getUnit().getId(),
                    Collectors.mapping(MaintenanceReminder::getMilestone, Collectors.toSet())));
        } catch (RuntimeException exception) {
            // a batch that cannot be read is skipped; the next batch and the next run still go on
            log.warn("Maintenance reminders: could not read a batch of {} vehicle(s): {}", ids.size(), exception.getMessage());
            return 0;
        }
        int announced = 0;
        for (OrderItemUnit unit : batch) {
            try {
                Integer count = perVehicle.execute(status -> remind(unit, handled.getOrDefault(unit.getId(), Set.of()), now));
                announced += count == null ? 0 : count;
            } catch (RuntimeException exception) {
                log.warn("Maintenance reminder for vehicle {} failed, left for the next run: {}", unit.getId(), exception.getMessage());
            }
        }
        return announced;
    }

    /** Stores the milestones that are due and announces the latest one; returns 1 when an announcement was made, else 0. */
    private int remind(OrderItemUnit unit, Set<String> alreadyHandled, LocalDateTime now) {
        LocalDateTime starts = WarrantyService.startsAt(unit);
        Set<String> done = new HashSet<>(alreadyHandled);
        List<Period> missing = WarrantyRules.dueMilestones(starts, intervals, now).stream()
            .filter(period -> !done.contains(period.toString()))
            .toList();
        if (missing.isEmpty()) {
            return 0;
        }
        Period latest = missing.get(missing.size() - 1);
        List<MaintenanceReminder> toSave = new ArrayList<>();
        for (Period period : missing) {
            MaintenanceReminder row = new MaintenanceReminder();
            row.setUnit(unit);
            row.setMilestone(period.toString());
            row.setDueAt(WarrantyRules.dueAt(starts, period));
            row.setNotified(period.equals(latest));
            toSave.add(row);
        }
        reminders.saveAll(toSave);
        User owner = unit.getOrderItem().getOrder().getUser();
        if (owner == null) {
            return 0;
        }
        NotificationEventBridge.publish(new MaintenanceDueEvent(
            owner.getId(), unit.getOrderItem().getProductName(), unit.getFrameNumber(), latest.toString()));
        return 1;
    }
}
