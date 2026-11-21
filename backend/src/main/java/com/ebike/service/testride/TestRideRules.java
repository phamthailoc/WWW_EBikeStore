package com.ebike.service.testride;

import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.testride.TestRideBooking;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The pure rules of V23 (opening hours, slots, steps of a booking), kept apart from the database so they can be tested alone. */
public final class TestRideRules {

    /** A booking that still holds a place in its slot. */
    public static final Set<String> OPEN = Set.of(TestRideBooking.REQUESTED, TestRideBooking.CONFIRMED);
    public static final Set<String> ALL = Set.of(
        TestRideBooking.REQUESTED, TestRideBooking.CONFIRMED, TestRideBooking.DONE, TestRideBooking.CANCELLED, TestRideBooking.NO_SHOW);
    /** Every slot is one hour long. */
    public static final Duration SLOT = Duration.ofHours(1);
    /** The hours used for a showroom whose opening hours cannot be read from the free text. */
    public static final LocalTime DEFAULT_OPEN = LocalTime.of(8, 0);
    public static final LocalTime DEFAULT_CLOSE = LocalTime.of(21, 0);
    /** A ride may be marked done from this long before its start (a customer who arrives a little early). */
    public static final Duration EARLY_DONE = Duration.ofMinutes(30);

    private static final Pattern HOURS = Pattern.compile("(\\d{1,2})\\s*(?:[:hH]\\s*(\\d{2})?)?\\s*[-\\u2013\\u2014]\\s*(\\d{1,2})\\s*(?:[:hH]\\s*(\\d{2})?)?");
    private static final Pattern PHONE = Pattern.compile("^0\\d{9,10}$");

    private TestRideRules() {
    }

    /** The opening time and closing time of a showroom. */
    public record Hours(LocalTime open, LocalTime close) {
    }

    /**
     * Reads "08:00 - 21:00" (also "8h - 21h", "08:30-17:00") from the free text of {@code showrooms.opening_hours}. Anything that cannot be
     * read as a same-day range falls back to 08:00 - 21:00, so a showroom with a note such as "by appointment" still takes bookings.
     */
    public static Hours parseOpeningHours(String text) {
        if (text == null) {
            return new Hours(DEFAULT_OPEN, DEFAULT_CLOSE);
        }
        Matcher matcher = HOURS.matcher(text);
        if (!matcher.find()) {
            return new Hours(DEFAULT_OPEN, DEFAULT_CLOSE);
        }
        LocalTime open = time(matcher.group(1), matcher.group(2));
        LocalTime close = time(matcher.group(3), matcher.group(4));
        if (open == null || close == null || !close.isAfter(open)) {
            return new Hours(DEFAULT_OPEN, DEFAULT_CLOSE);
        }
        return new Hours(open, close);
    }

    private static LocalTime time(String hour, String minute) {
        int h = Integer.parseInt(hour);
        int m = minute == null ? 0 : Integer.parseInt(minute);
        return h > 23 || m > 59 ? null : LocalTime.of(h, m);
    }

    /** The start times of the slots of one day: from the opening time in steps of one hour while the whole slot ends by the closing time. */
    public static List<LocalDateTime> slotsOn(LocalDate date, Hours hours) {
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime start = date.atTime(hours.open());
        LocalDateTime close = date.atTime(hours.close());
        while (!start.plus(SLOT).isAfter(close)) {
            slots.add(start);
            start = start.plus(SLOT);
        }
        return slots;
    }

    /** True when {@code slot} is one of the showroom's slots of its day. */
    public static boolean isOnGrid(LocalDateTime slot, Hours hours) {
        return slotsOn(slot.toLocalDate(), hours).contains(slot);
    }

    /** The earliest start a customer may book: {@code leadMinutes} from now. */
    public static boolean isFarEnough(LocalDateTime slot, LocalDateTime now, int leadMinutes) {
        return !slot.isBefore(now.plusMinutes(leadMinutes));
    }

    /** The last day a booking may be made for: {@code daysAhead} days after today. */
    public static boolean isWithinWindow(LocalDateTime slot, LocalDateTime now, int daysAhead) {
        return !slot.toLocalDate().isAfter(now.toLocalDate().plusDays(daysAhead));
    }

    /** A bike can be ridden; batteries and accessories cannot, and a product with no known vehicle type is not offered either. */
    public static boolean isRideable(VehicleType type) {
        return type != null && type != VehicleType.BATTERY && type != VehicleType.ACCESSORY;
    }

    /** Vietnamese mobile or landline number: starts with 0 and has 10 or 11 digits. */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(phone).matches();
    }

    /** Takes out the spaces, dots and dashes people type inside a number. */
    public static String normalizePhone(String phone) {
        return phone == null ? "" : phone.trim().replaceAll("[\\s.\\-]", "");
    }

    public static boolean isKnownStatus(String status) {
        return status != null && ALL.contains(status);
    }

    /** Which step may follow which. REQUESTED goes to CONFIRMED or CANCELLED; CONFIRMED to DONE, NO_SHOW or CANCELLED; the rest are final. */
    public static boolean canMove(String from, String to) {
        if (TestRideBooking.REQUESTED.equals(from)) {
            return TestRideBooking.CONFIRMED.equals(to) || TestRideBooking.CANCELLED.equals(to);
        }
        if (TestRideBooking.CONFIRMED.equals(from)) {
            return TestRideBooking.DONE.equals(to) || TestRideBooking.NO_SHOW.equals(to) || TestRideBooking.CANCELLED.equals(to);
        }
        return false;
    }

    /**
     * The steps the staff can take right now: the allowed steps, minus "done" before the ride starts (a little early is fine) and
     * minus "no-show" before the start time (nobody is absent from a ride that has not begun), and minus "confirm" once the slot began.
     */
    public static List<String> nextSteps(String status, LocalDateTime slotStart, LocalDateTime now) {
        List<String> steps = new ArrayList<>();
        for (String candidate : List.of(TestRideBooking.CONFIRMED, TestRideBooking.DONE, TestRideBooking.NO_SHOW, TestRideBooking.CANCELLED)) {
            if (canMove(status, candidate) && timingAllows(candidate, slotStart, now)) {
                steps.add(candidate);
            }
        }
        return steps;
    }

    /** The time condition of one step, separate from the order of the steps. */
    public static boolean timingAllows(String to, LocalDateTime slotStart, LocalDateTime now) {
        if (TestRideBooking.CONFIRMED.equals(to)) {
            return now.isBefore(slotStart);
        }
        if (TestRideBooking.DONE.equals(to)) {
            return !now.isBefore(slotStart.minus(EARLY_DONE));
        }
        if (TestRideBooking.NO_SHOW.equals(to)) {
            return !now.isBefore(slotStart);
        }
        return true;
    }

    /** A customer may cancel an open booking until its slot begins; after that only the staff close it. */
    public static boolean canCustomerCancel(String status, LocalDateTime slotStart, LocalDateTime now) {
        return OPEN.contains(status) && now.isBefore(slotStart);
    }

    /** The reminder goes to a confirmed booking that starts within the next {@code hours}, once. */
    public static boolean reminderDue(String status, LocalDateTime slotStart, LocalDateTime reminderSentAt, LocalDateTime now, int hours) {
        return TestRideBooking.CONFIRMED.equals(status) && reminderSentAt == null
            && slotStart.isAfter(now) && !slotStart.isAfter(now.plusHours(hours));
    }
}
