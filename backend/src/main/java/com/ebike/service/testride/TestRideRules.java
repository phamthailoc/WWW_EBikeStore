package com.ebike.service.testride;

import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.testride.TestRideBooking;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
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
    /** A closing time of 24:00: the slots run to the end of the day, to midnight of the next one. */
    public static final LocalTime END_OF_DAY = LocalTime.MAX;
    /** A ride may be marked done from this long before its start (a customer who arrives a little early). */
    public static final Duration EARLY_DONE = Duration.ofMinutes(30);

    /**
     * One clock time: {@code 08:00} (minutes required), {@code 8h} / {@code 8h30}, either with {@code AM} / {@code PM} after it, or an hour with
     * {@code AM} / {@code PM} alone ({@code 8 AM}). A bare number is not a time, so the days of "Thứ 2-7: 08:00 - 21:00" are not read as 02:00 - 07:00.
     * Groups: hour, minutes after a colon, minutes after an h, AM/PM after the minutes, AM/PM after a bare hour.
     */
    private static final String TIME = "(\\d{1,2})(?:(?::(\\d{2})|[hH](\\d{2})?)\\s*(?:([AaPp])\\.?[Mm]\\b\\.?)?|\\s*([AaPp])\\.?[Mm]\\b\\.?)";
    /** Two clock times joined by a dash: groups 1-5 are the opening time, 6-10 the closing time. */
    private static final Pattern HOURS = Pattern.compile(TIME + "\\s*[-\\u2013\\u2014]\\s*" + TIME);
    /** A weekday written as a word or abbreviation: Thứ 2 ... Thứ 7, T2 ... T7, CN, Chủ nhật, Mon ... Sun. */
    private static final Pattern WEEKDAY = Pattern.compile(
        "(?<![\\p{L}\\d])(?:th\\u1ee9\\s*([2-7])|t([2-7])|(cn|ch\\u1ee7\\s*nh\\u1eadt)|(mon|tue|wed|thu|fri|sat|sun)[a-z]*)(?![\\p{L}\\d])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** A clause that gives a weekday off: "CN đóng cửa", "Sunday closed", "T7 nghỉ". */
    private static final Pattern DAY_OFF = Pattern.compile("đóng\\s*cửa|dong\\s*cua|nghỉ|nghi|closed|(?<![\\p{L}\\d])off(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** The whole text is just "8 - 21": written with no minutes and no "h", it can only be hours. */
    private static final Pattern PLAIN_HOURS = Pattern.compile("^\\s*(\\d{1,2})\\s*[-\\u2013\\u2014]\\s*(\\d{1,2})\\s*$");
    /** Free text that says the showroom is shut ({@code Đóng cửa}, {@code Tạm nghỉ}, {@code Closed}), used only when no hours are written. */
    private static final Pattern SHUT = Pattern.compile("đóng\\s*cửa|dong\\s*cua|tạm\\s*(?:nghỉ|ngưng|ngừng)|tam\\s*(?:nghi|ngung)|ngừng\\s*hoạt\\s*động|closed", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PHONE = Pattern.compile("^0\\d{9,10}$");

    private TestRideRules() {
    }

    /**
     * The opening time and closing time of a showroom, and the weekdays it is shut. A close of {@link #END_OF_DAY} means 24:00, the end of the day.
     */
    public record Hours(LocalTime open, LocalTime close, Set<DayOfWeek> closedDays) {
        /** A showroom that is shut: no slot fits between the same two times. */
        public static final Hours CLOSED = new Hours(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT);

        public Hours {
            closedDays = Set.copyOf(closedDays);
        }

        /** Open every day. */
        public Hours(LocalTime open, LocalTime close) {
            this(open, close, Set.of());
        }
    }

    /**
     * Reads "08:00 - 21:00" (also "8h - 21h", "8:00 AM - 9:00 PM", "08:00 - 24:00", "08:30-17:00", and "Thứ 2-7: 08:00 - 21:00" as 08:00 - 21:00) from the
     * free text of {@code showrooms.opening_hours}. A clause that gives a day off with no hours in it ("CN đóng cửa", "Sunday closed", "T7 nghỉ") makes
     * that weekday take no bookings. Text that says the showroom is shut ("Đóng cửa", "Tạm nghỉ", "Closed") and gives no hours takes no bookings at all. Anything
     * else that cannot be read as a same-day range falls back to 08:00 - 21:00, so a showroom with a note such as "by appointment" still takes bookings.
     *
     * <p>Only the first range is read: a lunch break ("8:00 - 12:00, 13:30 - 21:00"), hours that differ by weekday, hours past midnight ("22:00 - 02:00")
     * and holidays are not understood (the first two use the first range, the third falls back to the default hours). The manager must keep the text to one
     * simple range and, at most, a weekday off.
     */
    public static Hours parseOpeningHours(String text) {
        if (text == null) {
            return new Hours(DEFAULT_OPEN, DEFAULT_CLOSE);
        }
        // clauses that only give a weekday off are taken out first, so "CN đóng cửa" is not read as "the showroom is shut"
        Set<DayOfWeek> closedDays = EnumSet.noneOf(DayOfWeek.class);
        StringBuilder rest = new StringBuilder();
        for (String clause : text.split("[;|\n,]")) {
            if (!HOURS.matcher(clause).find() && DAY_OFF.matcher(clause).find() && addWeekdays(clause, closedDays)) {
                continue;
            }
            rest.append(clause).append(' ');
        }
        Hours base = parseRange(closedDays.isEmpty() ? text : rest.toString());
        return closedDays.isEmpty() ? base : new Hours(base.open(), base.close(), closedDays);
    }

    /** Adds the weekdays named in the clause to {@code days}; false when it names none. */
    private static boolean addWeekdays(String clause, Set<DayOfWeek> days) {
        Matcher matcher = WEEKDAY.matcher(clause);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            if (matcher.group(1) != null) {
                days.add(DayOfWeek.of(Integer.parseInt(matcher.group(1)) - 1)); // Thứ 2 is Monday
            } else if (matcher.group(2) != null) {
                days.add(DayOfWeek.of(Integer.parseInt(matcher.group(2)) - 1));
            } else if (matcher.group(3) != null) {
                days.add(DayOfWeek.SUNDAY);
            } else {
                days.add(switch (matcher.group(4).toLowerCase(java.util.Locale.ROOT)) {
                    case "mon" -> DayOfWeek.MONDAY;
                    case "tue" -> DayOfWeek.TUESDAY;
                    case "wed" -> DayOfWeek.WEDNESDAY;
                    case "thu" -> DayOfWeek.THURSDAY;
                    case "fri" -> DayOfWeek.FRIDAY;
                    case "sat" -> DayOfWeek.SATURDAY;
                    default -> DayOfWeek.SUNDAY;
                });
            }
        }
        return found;
    }

    private static Hours parseRange(String text) {
        Matcher matcher = HOURS.matcher(text);
        LocalTime open;
        LocalTime close;
        if (matcher.find()) {
            open = time(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3), matcher.group(4) != null ? matcher.group(4) : matcher.group(5));
            close = time(matcher.group(6), matcher.group(7) != null ? matcher.group(7) : matcher.group(8), matcher.group(9) != null ? matcher.group(9) : matcher.group(10));
        } else {
            Matcher plain = PLAIN_HOURS.matcher(text);
            if (!plain.matches()) {
                return SHUT.matcher(text).find() ? Hours.CLOSED : new Hours(DEFAULT_OPEN, DEFAULT_CLOSE);
            }
            open = time(plain.group(1), null, null);
            close = time(plain.group(2), null, null);
        }
        if (open == null || close == null || !close.isAfter(open)) {
            return new Hours(DEFAULT_OPEN, DEFAULT_CLOSE);
        }
        return new Hours(open, close);
    }

    /** {@code meridiem} is "a" or "p" (any case) or null. 24:00 is {@link #END_OF_DAY}; an hour or minute that does not exist is null. */
    private static LocalTime time(String hour, String minute, String meridiem) {
        int h = Integer.parseInt(hour);
        int m = minute == null ? 0 : Integer.parseInt(minute);
        if (meridiem != null) {
            if (h < 1 || h > 12) {
                return null;
            }
            h = h % 12 + ("p".equalsIgnoreCase(meridiem) ? 12 : 0);
        }
        if (h == 24 && m == 0) {
            return END_OF_DAY;
        }
        return h > 23 || m > 59 ? null : LocalTime.of(h, m);
    }

    /** The start times of the slots of one day: from the opening time in steps of one hour while the whole slot ends by the closing time. */
    public static List<LocalDateTime> slotsOn(LocalDate date, Hours hours) {
        List<LocalDateTime> slots = new ArrayList<>();
        if (hours.closedDays().contains(date.getDayOfWeek())) {
            return slots;
        }
        LocalDateTime start = date.atTime(hours.open());
        LocalDateTime close = END_OF_DAY.equals(hours.close()) ? date.plusDays(1).atStartOfDay() : date.atTime(hours.close());
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

    /** The same rule for the name of a vehicle type as a page or response carries it; null and a name that is no vehicle type are not rideable. */
    public static boolean isRideableName(String vehicleTypeName) {
        if (vehicleTypeName == null) {
            return false;
        }
        try {
            return isRideable(VehicleType.valueOf(vehicleTypeName.trim()));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    /** Vietnamese mobile or landline number: starts with 0 and has 10 or 11 digits. */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(phone).matches();
    }

    /** Takes out the spaces, dots, dashes and brackets people type inside a number, and turns the international form (+84901234567, 84901234567) into the local one (0901234567). */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.trim().replaceAll("[\\s.\\-()]", "");
        String rest;
        if (digits.startsWith("+84")) {
            rest = digits.substring(3);
        } else if (digits.startsWith("84") && (digits.length() == 11 || digits.length() == 12)) {
            rest = digits.substring(2);
        } else {
            return digits;
        }
        return rest.startsWith("0") ? rest : "0" + rest;
    }

    public static boolean isKnownStatus(String status) {
        return status != null && ALL.contains(status);
    }

    /**
     * Which step may follow which. REQUESTED goes to CONFIRMED or CANCELLED, and also to DONE or NO_SHOW (a customer who came at the time of a
     * request nobody had confirmed is still a ride that happened, or a no-show; the clock rules of {@link #timingAllows} keep it from being early);
     * CONFIRMED to DONE, NO_SHOW or CANCELLED; the rest are final.
     */
    public static boolean canMove(String from, String to) {
        if (TestRideBooking.REQUESTED.equals(from)) {
            return TestRideBooking.CONFIRMED.equals(to) || TestRideBooking.CANCELLED.equals(to)
                || TestRideBooking.DONE.equals(to) || TestRideBooking.NO_SHOW.equals(to);
        }
        if (TestRideBooking.CONFIRMED.equals(from)) {
            return TestRideBooking.DONE.equals(to) || TestRideBooking.NO_SHOW.equals(to) || TestRideBooking.CANCELLED.equals(to);
        }
        return false;
    }

    /** {@link #nextSteps(String, LocalDateTime, LocalDateTime, boolean)} for a booking whose showroom and bike are both still available. */
    public static List<String> nextSteps(String status, LocalDateTime slotStart, LocalDateTime now) {
        return nextSteps(status, slotStart, now, true);
    }

    /**
     * The steps the staff can take right now: the allowed steps, minus "done" before the ride starts (a little early is fine) and
     * minus "no-show" before the start time (nobody is absent from a ride that has not begun), minus "confirm" once the slot began or when the
     * showroom or the bike is no longer available ({@code available} false: the ride cannot take place), and minus "cancel" once the slot is over.
     */
    public static List<String> nextSteps(String status, LocalDateTime slotStart, LocalDateTime now, boolean available) {
        List<String> steps = new ArrayList<>();
        for (String candidate : List.of(TestRideBooking.CONFIRMED, TestRideBooking.DONE, TestRideBooking.NO_SHOW, TestRideBooking.CANCELLED)) {
            if (canMove(status, candidate) && timingAllows(candidate, slotStart, now) && (available || !TestRideBooking.CONFIRMED.equals(candidate))) {
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
        if (TestRideBooking.CANCELLED.equals(to)) {
            // a ride whose hour is over did happen or did not: it is closed as done or no-show, and the customer is not told "we cancelled your ride" about a past one
            return now.isBefore(slotStart.plus(SLOT));
        }
        return true;
    }

    /** A customer may cancel an open booking until its slot begins; after that only the staff close it. */
    public static boolean canCustomerCancel(String status, LocalDateTime slotStart, LocalDateTime now) {
        return OPEN.contains(status) && now.isBefore(slotStart);
    }

    /** The last start time of a request that is too old to wait for a confirmation: its whole slot is over by {@code now}. */
    public static LocalDateTime expiryCutoff(LocalDateTime now) {
        return now.minus(SLOT);
    }

    /** The reminder goes to a confirmed booking that starts within the next {@code hours}, once. */
    public static boolean reminderDue(String status, LocalDateTime slotStart, LocalDateTime reminderSentAt, LocalDateTime now, int hours) {
        return TestRideBooking.CONFIRMED.equals(status) && reminderSentAt == null
            && slotStart.isAfter(now) && !slotStart.isAfter(now.plusHours(hours));
    }
}
