package com.ebike.service.warranty;

import com.ebike.model.entity.warranty.WarrantyClaim;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The pure rules of V20 (warranty dates, maintenance milestones, claim steps), kept apart from the database so they can be tested alone. */
public final class WarrantyRules {

    /** First service after a month, then every six months: the plan's default when nothing is configured. */
    public static final String DEFAULT_INTERVALS = "P1M,P6M,P12M";

    public static final String ACTIVE = "ACTIVE";
    public static final String EXPIRED = "EXPIRED";
    /** The product has no warranty length on record. */
    public static final String UNKNOWN = "UNKNOWN";
    /** Sold but not handed over yet, so the warranty has not started. */
    public static final String NOT_STARTED = "NOT_STARTED";

    private static final Map<String, Set<String>> NEXT_STEPS = Map.of(
        WarrantyClaim.NEW, Set.of(WarrantyClaim.RECEIVED, WarrantyClaim.REJECTED),
        WarrantyClaim.RECEIVED, Set.of(WarrantyClaim.REPAIRING, WarrantyClaim.DONE, WarrantyClaim.REJECTED),
        WarrantyClaim.REPAIRING, Set.of(WarrantyClaim.DONE, WarrantyClaim.REJECTED),
        WarrantyClaim.DONE, Set.of(),
        WarrantyClaim.REJECTED, Set.of()
    );

    private WarrantyRules() {
    }

    /** The warranty starts when the bike was handed over: the shipment's delivery time, else the time the order last changed. */
    public static LocalDateTime startsAt(LocalDateTime deliveredAt, LocalDateTime orderUpdatedAt) {
        return deliveredAt != null ? deliveredAt : orderUpdatedAt;
    }

    /** Null when the start or the length is unknown, or the length is not positive. */
    public static LocalDateTime endsAt(LocalDateTime startsAt, Integer warrantyMonths) {
        if (startsAt == null || warrantyMonths == null || warrantyMonths <= 0) {
            return null;
        }
        return startsAt.plusMonths(warrantyMonths);
    }

    /** The warranty covers the bike up to and including its last moment. */
    public static String coverage(LocalDateTime startsAt, Integer warrantyMonths, LocalDateTime now) {
        if (startsAt == null) {
            return NOT_STARTED;
        }
        LocalDateTime ends = endsAt(startsAt, warrantyMonths);
        if (ends == null) {
            return UNKNOWN;
        }
        return now.isAfter(ends) ? EXPIRED : ACTIVE;
    }

    /**
     * Reads {@code P1M,P6M,P12M}. Blank or unreadable parts are skipped, zero and negative periods too; the result is sorted
     * shortest first without repeats, and falls back to the default when nothing usable is left.
     */
    public static List<Period> parseIntervals(String spec) {
        List<Period> parsed = parse(spec);
        return parsed.isEmpty() ? parse(DEFAULT_INTERVALS) : parsed;
    }

    private static List<Period> parse(String spec) {
        Map<String, Period> found = new LinkedHashMap<>();
        for (String part : (spec == null ? "" : spec).split(",")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                Period period = Period.parse(text).normalized();
                if (!period.isNegative() && !period.isZero()) {
                    found.putIfAbsent(period.toString(), period);
                }
            } catch (DateTimeParseException ignored) {
                // a typo in the setting must not stop the job; the other values still work
            }
        }
        List<Period> sorted = new ArrayList<>(found.values());
        sorted.sort((a, b) -> Long.compare(a.toTotalMonths() * 31 + a.getDays(), b.toTotalMonths() * 31 + b.getDays()));
        return sorted;
    }

    public static LocalDateTime dueAt(LocalDateTime startsAt, Period interval) {
        return startsAt.plus(interval);
    }

    /** The milestones already due at {@code now}, shortest first. */
    public static List<Period> dueMilestones(LocalDateTime startsAt, List<Period> intervals, LocalDateTime now) {
        List<Period> due = new ArrayList<>();
        if (startsAt == null) {
            return due;
        }
        for (Period interval : intervals) {
            if (!dueAt(startsAt, interval).isAfter(now)) {
                due.add(interval);
            }
        }
        return due;
    }

    /** The first milestone still ahead, or null when the last one has passed. */
    public static LocalDateTime nextMaintenanceAt(LocalDateTime startsAt, List<Period> intervals, LocalDateTime now) {
        if (startsAt == null) {
            return null;
        }
        for (Period interval : intervals) {
            LocalDateTime due = dueAt(startsAt, interval);
            if (due.isAfter(now)) {
                return due;
            }
        }
        return null;
    }

    public static boolean isOpen(String status) {
        return WarrantyClaim.NEW.equals(status) || WarrantyClaim.RECEIVED.equals(status) || WarrantyClaim.REPAIRING.equals(status);
    }

    public static boolean isKnownStatus(String status) {
        return NEXT_STEPS.containsKey(status);
    }

    /** Staying on the same step is allowed (it only changes the staff note); finished claims never move again. */
    public static boolean canMove(String from, String to) {
        if (from == null || to == null || !NEXT_STEPS.containsKey(from) || !NEXT_STEPS.containsKey(to)) {
            return false;
        }
        if (from.equals(to)) {
            return isOpen(from);
        }
        return NEXT_STEPS.get(from).contains(to);
    }
}
