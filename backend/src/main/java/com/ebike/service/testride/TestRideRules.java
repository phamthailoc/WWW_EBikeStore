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
    /** A weekday written as a word or abbreviation: Thứ 2 ... Thứ 7, Thứ Hai ... Thứ Bảy (also without accents), T2 ... T7, CN, Chủ nhật, Mon ... Sun (also Monday ...). Case-insensitive. */
    private static final String DAY = "(?:thứ\\s*(?:[2-7]|hai|ba|tư|năm|sáu|bảy)|thu\\s*(?:[2-7]|hai|ba|tu|nam|sau|bay)|t[2-7]|cn|chủ\\s*nhật|chu\\s*nhat|(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tues|tue|wed|thurs|thur|thu|fri|sat|sun)s?)";
    /** One weekday or a range of them ("T2-T6", "Thứ 2 - Thứ 6", "Thứ 2-6", "Mon-Fri", "Sat to Sun"; "month" is no weekday): group 1 the first day, group 2 the last day of a range (a day or a bare 2-7). */
    private static final Pattern WEEKDAYS = Pattern.compile(
        "(?<![\\p{L}\\d])(" + DAY + ")(?:\\s*(?:[-–—]|to|đến|tới)\\s*(" + DAY + "|[2-7]))?(?![\\p{L}\\d])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** A clause that gives a weekday off: "CN đóng cửa", "Sunday closed", "T7 nghỉ", "trừ Chủ nhật", "except Sunday". Each word stands alone, so "nghiệp" or "official" do not count. */
    private static final Pattern DAY_OFF = Pattern.compile(
        "(?<![\\p{L}\\d])(?:đóng\\s*cửa|dong\\s*cua|nghỉ|nghi|closed|off|trừ|tru|except|excluding)(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** The whole text is just "8 - 21": written with no minutes and no "h", it can only be hours. */
    private static final Pattern PLAIN_HOURS = Pattern.compile("^\\s*(\\d{1,2})\\s*[-\\u2013\\u2014]\\s*(\\d{1,2})\\s*$");
    /** An hour with no unit before a dash and a time that has one: "8 - 21h", "8-9 PM". Group 1 the opening hour, groups 2-6 the closing time. A number that follows a letter (the 2 of "T2") is not an hour. */
    private static final Pattern HOURS_BARE_OPEN = Pattern.compile("(?<![\\p{L}\\d:.\\-–—])(\\d{1,2})\\s*[-–—]\\s*" + TIME);
    /** A phrase that says the showroom is shut (Đóng cửa, Tạm nghỉ, Closed); it counts only when the whole text says nothing else. */
    private static final Pattern SHUT = Pattern.compile(
        "(?<![\\p{L}\\d])(?:đóng\\s*cửa|dong\\s*cua|tạm\\s*(?:nghỉ|ngưng|ngừng)|tam\\s*(?:nghi|ngung)|ngừng\\s*hoạt\\s*động|ngưng\\s*hoạt\\s*động|nghỉ|closed)(?![\\p{L}\\d])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Words that make a "closed" only part of the story: a holiday, a break, a part of the day, or the showroom being open too. */
    private static final Pattern SHUT_QUALIFIER = Pattern.compile(
        "(?<![\\p{L}\\d])(?:lễ|tết|holidays?|trưa|lunch|break|buổi|sáng|chiều|tối|đêm|morning|afternoon|evening|night|mở\\s*cửa|mo\\s*cua|open)(?![\\p{L}\\d])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** "thứ hai" ... "thứ bảy" (lower case): group 1 the number written as a word. */
    private static final Pattern DAY_NAME = Pattern.compile("th[uứ]\\s*(hai|ba|tư|tu|năm|nam|sáu|sau|bảy|bay)");
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
     * Reads "08:00 - 21:00" (also "8h - 21h", "8 - 21h", "8:00 AM - 9:00 PM", "08:00 - 24:00", "08:30-17:00", and "Thứ 2-7: 08:00 - 21:00" as 08:00 - 21:00) from the
     * free text of {@code showrooms.opening_hours}. A clause that gives a day off with no hours in it ("CN đóng cửa", "Sunday closed", "T7 nghỉ", "T7-CN nghỉ",
     * "Sat-Sun closed") makes those weekdays (a range counts every day in it) take no bookings; the same goes for a day off written after the hours in one
     * clause ("Mon-Sat 8:00 - 21:00 Sun closed", "8:00 - 21:00 trừ Chủ nhật", "8:00 - 21:00 except Sunday"). When every range of hours is written after
     * the weekdays it is for ("T2 - T7: 8:00 - 20:00"), the other weekdays are shut. Weekdays may be written "Thứ Hai" ... "Thứ Bảy" too. A list of days split by commas is read whatever its length and wherever the "off" stands ("Nghỉ T7, CN",
     * "T6, T7, CN nghỉ", "closed Sat, Sun", "08:00 - 21:00 (T7, CN nghỉ)", "CN, ngày lễ: nghỉ"). Text whose every clause says the showroom is shut ("Đóng cửa", "Tạm nghỉ", "Closed") and gives no hours takes
     * no bookings at all; "Mở cửa hằng ngày, đóng cửa ngày lễ" is not that, since it also says "open" and names a holiday. Anything else that cannot be read
     * as a same-day range falls back to 08:00 - 21:00, so a showroom with a note such as "by appointment" still takes bookings.
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
        Set<DayOfWeek> listed = EnumSet.noneOf(DayOfWeek.class); // the clauses just before this one that name only weekdays ("T6, T7, CN nghỉ")
        Set<DayOfWeek> openDays = EnumSet.noneOf(DayOfWeek.class); // the weekdays written in front of hours ("T2 - T7: 8 - 20")
        boolean everyHoursNamesDays = true;
        boolean afterOff = false; // the clause before was a day off, so a clause of weekdays that follows is off too ("Nghỉ T7, CN")
        StringBuilder rest = new StringBuilder();
        for (String clause : text.split("[;|\n,]")) {
            Matcher hours = hoursIn(clause);
            if (hours != null) {
                String head = clause.substring(0, hours.start());
                if (onlyWeekdays(head)) {
                    addWeekdays(head, openDays);
                } else {
                    everyHoursNamesDays = false;
                }
                // "Mon-Sat 8:00 - 21:00 Sun closed": a day off written after the hours of the same clause
                String tail = clause.substring(hours.end());
                listed.clear();
                afterOff = false;
                if (DAY_OFF.matcher(tail).find() && addWeekdays(tail, closedDays)) {
                    clause = clause.substring(0, hours.end());
                    afterOff = true;
                } else if (tail.indexOf('(') >= 0 && onlyWeekdays(tail)) {
                    addWeekdays(tail, listed); // "08:00 - 21:00 (T7, CN nghỉ)": the list goes on in the next clause
                }
            } else if (DAY_OFF.matcher(clause).find()) {
                Set<DayOfWeek> named = EnumSet.noneOf(DayOfWeek.class);
                if (addWeekdays(clause, named) || !listed.isEmpty()) { // "CN, ngày lễ: nghỉ": the Sunday before it is off too
                    closedDays.addAll(named);
                    closedDays.addAll(listed);
                    listed.clear();
                    afterOff = true;
                    continue;
                }
                listed.clear();
                afterOff = false;
            } else if (onlyWeekdays(clause)) {
                if (afterOff) {
                    addWeekdays(clause, closedDays);
                    continue;
                }
                addWeekdays(clause, listed);
            } else {
                listed.clear();
                afterOff = false;
            }
            rest.append(clause).append(' ');
        }
        if (everyHoursNamesDays && !openDays.isEmpty()) {
            // "T2 - T7: 8:00 - 20:00": a weekday that no hours are written for is shut
            for (DayOfWeek day : DayOfWeek.values()) {
                if (!openDays.contains(day)) {
                    closedDays.add(day);
                }
            }
        }
        Hours base = parseRange(closedDays.isEmpty() ? text : rest.toString());
        return closedDays.isEmpty() ? base : new Hours(base.open(), base.close(), closedDays);
    }

    /** A matcher that found the first hours range written in the clause (so the caller may ask where it ends), or null. */
    private static Matcher hoursIn(String clause) {
        Matcher matcher = HOURS.matcher(clause);
        if (matcher.find()) {
            return matcher;
        }
        matcher = HOURS_BARE_OPEN.matcher(clause);
        return matcher.find() ? matcher : null;
    }

    /** True when the clause is nothing but weekdays and the words that join them ("T7", "Thứ 6 và Thứ 7", "Sat & Sun"). */
    private static boolean onlyWeekdays(String clause) {
        String left = WEEKDAYS.matcher(clause).replaceAll("");
        return !left.equals(clause) && left.replaceAll("(?iu)(?<![\\p{L}\\d])(?:và|and)(?![\\p{L}\\d])|[\\s&/:.()\\-–—]", "").isEmpty();
    }

    /** Adds the weekdays named in the clause (a range such as "T2-T6" or "Mon-Fri" gives every day in it) to {@code days}; false when it names none. */
    private static boolean addWeekdays(String clause, Set<DayOfWeek> days) {
        Matcher matcher = WEEKDAYS.matcher(clause);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            DayOfWeek day = dayOf(matcher.group(1));
            days.add(day);
            if (matcher.group(2) != null) {
                DayOfWeek last = dayOf(matcher.group(2));
                while (day != last) { // forward from the first day, so "T7 - T2" runs Saturday, Sunday, Monday
                    day = day.plus(1);
                    days.add(day);
                }
            }
        }
        return found;
    }

    /** One weekday word: "Thứ 2" is Monday ... "Thứ 7" Saturday, "CN" Sunday; a bare digit 2-7 (the end of "Thứ 2-6") counts the same way. */
    private static DayOfWeek dayOf(String word) {
        String w = word.trim().toLowerCase(java.util.Locale.ROOT);
        char last = w.charAt(w.length() - 1);
        if (Character.isDigit(last)) {
            return DayOfWeek.of(last - '0' - 1); // Thứ 2 is Monday
        }
        if (w.startsWith("cn") || w.startsWith("ch")) {
            return DayOfWeek.SUNDAY;
        }
        Matcher named = DAY_NAME.matcher(w);
        if (named.matches()) { // "thứ hai" ... "thứ bảy", with or without accents
            return switch (named.group(1)) {
                case "hai" -> DayOfWeek.MONDAY;
                case "ba" -> DayOfWeek.TUESDAY;
                case "tư", "tu" -> DayOfWeek.WEDNESDAY;
                case "năm", "nam" -> DayOfWeek.THURSDAY;
                case "sáu", "sau" -> DayOfWeek.FRIDAY;
                default -> DayOfWeek.SATURDAY;
            };
        }
        return switch (w.substring(0, 3)) {
            case "mon" -> DayOfWeek.MONDAY;
            case "tue" -> DayOfWeek.TUESDAY;
            case "wed" -> DayOfWeek.WEDNESDAY;
            case "thu" -> DayOfWeek.THURSDAY;
            case "fri" -> DayOfWeek.FRIDAY;
            case "sat" -> DayOfWeek.SATURDAY;
            default -> DayOfWeek.SUNDAY;
        };
    }

    private static Hours parseRange(String text) {
        Matcher matcher = HOURS.matcher(text);
        LocalTime open;
        LocalTime close;
        if (matcher.find()) {
            open = time(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3), matcher.group(4) != null ? matcher.group(4) : matcher.group(5));
            close = time(matcher.group(6), matcher.group(7) != null ? matcher.group(7) : matcher.group(8), matcher.group(9) != null ? matcher.group(9) : matcher.group(10));
        } else if ((matcher = HOURS_BARE_OPEN.matcher(text)).find()) {
            open = time(matcher.group(1), null, null);
            close = time(matcher.group(2), matcher.group(3) != null ? matcher.group(3) : matcher.group(4), matcher.group(5) != null ? matcher.group(5) : matcher.group(6));
        } else {
            Matcher plain = PLAIN_HOURS.matcher(text);
            if (!plain.matches()) {
                return isShut(text) ? Hours.CLOSED : new Hours(DEFAULT_OPEN, DEFAULT_CLOSE);
            }
            open = time(plain.group(1), null, null);
            close = time(plain.group(2), null, null);
        }
        if (open == null || close == null || !close.isAfter(open)) {
            return new Hours(DEFAULT_OPEN, DEFAULT_CLOSE);
        }
        return new Hours(open, close);
    }

    /** True when every clause of the text says the showroom is shut and none adds a holiday, a break, a part of the day or "open": "Đóng cửa", "Tạm nghỉ", "Closed". */
    private static boolean isShut(String text) {
        boolean shut = false;
        for (String clause : text.split("[;|\n,.]")) {
            if (clause.replaceAll("[^\\p{L}\\d]", "").isEmpty()) {
                continue;
            }
            if (!SHUT.matcher(clause).find() || SHUT_QUALIFIER.matcher(clause).find()) {
                return false;
            }
            shut = true;
        }
        return shut;
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
     * Which step may follow which. REQUESTED goes to CONFIRMED or CANCELLED, and to DONE (a customer who came a little early to a request nobody had
     * confirmed yet; the clock rules of {@link #timingAllows} allow it only from {@link #EARLY_DONE} before the start), but never to NO_SHOW: nobody is absent
     * from a ride the shop never agreed to, and a request is closed by the timer once its start time comes. CONFIRMED goes to DONE, NO_SHOW or CANCELLED; the rest are final.
     */
    public static boolean canMove(String from, String to) {
        if (TestRideBooking.REQUESTED.equals(from)) {
            return TestRideBooking.CONFIRMED.equals(to) || TestRideBooking.CANCELLED.equals(to) || TestRideBooking.DONE.equals(to);
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

    /**
     * The last start time of a request that is too old to wait for a confirmation: its start time has come by {@code now}. From then it can no longer be
     * confirmed, so it is closed (and the customer told) instead of staying open past the appointment.
     */
    public static LocalDateTime expiryCutoff(LocalDateTime now) {
        return now;
    }

    /** The reminder goes to a confirmed booking that starts within the next {@code hours}, once. */
    public static boolean reminderDue(String status, LocalDateTime slotStart, LocalDateTime reminderSentAt, LocalDateTime now, int hours) {
        return TestRideBooking.CONFIRMED.equals(status) && reminderSentAt == null
            && slotStart.isAfter(now) && !slotStart.isAfter(now.plusHours(hours));
    }
}
