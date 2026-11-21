package com.ebike.service.testride;

import com.ebike.common.i18n.RequestLanguage;
import com.ebike.dto.testride.TestRideBacklogResponse;
import com.ebike.dto.testride.TestRideCreateRequest;
import com.ebike.dto.testride.TestRideResponse;
import com.ebike.dto.testride.TestRideSlotResponse;
import com.ebike.dto.testride.TestRideUpdateRequest;
import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.service.admin.AuditLogService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * V23: test-ride bookings. A customer picks a bike, a showroom and a one-hour slot within the next days; every slot holds a fixed
 * number of rides per showroom. The staff confirm the request, close it afterwards (done, no-show) or cancel it.
 *
 * <p>The number of places is kept without a table of slots: booking locks the showroom row first and counts the open bookings of the slot
 * under that lock, so two customers cannot both take the last place; it then locks the customer's row so one customer's parallel requests are counted one by one. A customer cannot hold two open bookings at the same time (the
 * database refuses it too) nor more than {@code app.test-ride.max-open-per-customer} future ones.
 */
@Service
public class TestRideService {

    public static final String DEFAULT_CAPACITY = "2";
    /** The status filter that lists every status, open or closed. */
    public static final String ALL = "ALL";
    /** A staff list shows at most this many bookings (a day of every showroom is far below it). */
    public static final int SCHEDULE_LIMIT = 200;
    /** What a customer's own list shows at most, newest slot first. */
    public static final int MINE_LIMIT = 100;
    static final int MAX_NOTE = 500;
    static final int MAX_STAFF_NOTE = 1000;
    private static final LocalDateTime FAR_PAST = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2100, 1, 1, 0, 0);

    private final TestRideBookingRepository bookings;
    private final ShowroomRepository showrooms;
    private final ProductRepository products;
    private final UserRepository users;
    private final AuditLogService auditLog;
    private final int daysAhead;
    private final int capacity;
    private final int leadMinutes;
    private final int maxOpenPerCustomer;
    private final int reminderHours;
    private final Clock clock;

    @Autowired
    public TestRideService(
        TestRideBookingRepository bookings,
        ShowroomRepository showrooms,
        ProductRepository products,
        UserRepository users,
        AuditLogService auditLog,
        @Value("${app.test-ride.days-ahead:14}") int daysAhead,
        @Value("${app.test-ride.capacity-per-slot:" + DEFAULT_CAPACITY + "}") int capacity,
        @Value("${app.test-ride.lead-minutes:120}") int leadMinutes,
        @Value("${app.test-ride.max-open-per-customer:3}") int maxOpenPerCustomer,
        @Value("${app.test-ride.reminder-hours:24}") int reminderHours
    ) {
        this(bookings, showrooms, products, users, auditLog, daysAhead, capacity, leadMinutes, maxOpenPerCustomer, reminderHours, Clock.systemDefaultZone());
    }

    TestRideService(
        TestRideBookingRepository bookings,
        ShowroomRepository showrooms,
        ProductRepository products,
        UserRepository users,
        AuditLogService auditLog,
        int daysAhead,
        int capacity,
        int leadMinutes,
        int maxOpenPerCustomer,
        int reminderHours,
        Clock clock
    ) {
        this.bookings = bookings;
        this.showrooms = showrooms;
        this.products = products;
        this.users = users;
        this.auditLog = auditLog;
        this.daysAhead = Math.max(1, daysAhead);
        this.capacity = Math.max(1, capacity);
        this.leadMinutes = Math.max(0, leadMinutes);
        this.maxOpenPerCustomer = Math.max(1, maxOpenPerCustomer);
        this.reminderHours = Math.max(1, reminderHours);
        this.clock = clock;
    }

    /** The slots of one showroom on one day, with the places left in each. */
    @Transactional(readOnly = true)
    public List<TestRideSlotResponse> slots(Long showroomId, String date) {
        if (showroomId == null) {
            throw bad("Vui lòng chọn cửa hàng.", "Please choose a showroom.");
        }
        LocalDate day = parseDate(date);
        LocalDateTime now = LocalDateTime.now(clock);
        if (day.isBefore(now.toLocalDate()) || day.isAfter(now.toLocalDate().plusDays(daysAhead))) {
            throw bad("Chỉ đặt lịch trong " + daysAhead + " ngày tới.", "You can book only within the next " + daysAhead + " days.");
        }
        Showroom showroom = showrooms.findById(showroomId).filter(found -> Boolean.TRUE.equals(found.getActive()))
            .orElseThrow(TestRideService::showroomNotFound);
        TestRideRules.Hours hours = TestRideRules.parseOpeningHours(showroom.getOpeningHours());
        Map<LocalDateTime, Long> taken = new HashMap<>();
        for (Object[] row : bookings.countBySlot(showroomId, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), TestRideRules.OPEN)) {
            taken.put(asDateTime(row[0]), ((Number) row[1]).longValue());
        }
        return TestRideRules.slotsOn(day, hours).stream().map(slot -> {
            int remaining = (int) Math.max(0, capacity - taken.getOrDefault(slot, 0L));
            boolean farEnough = TestRideRules.isFarEnough(slot, now, leadMinutes);
            return new TestRideSlotResponse(slot, capacity, remaining, remaining > 0 && farEnough);
        }).toList();
    }

    /** The signed-in customer's bookings, latest slot first. */
    @Transactional(readOnly = true)
    public List<TestRideResponse> mine(String username) {
        requireUsername(username);
        LocalDateTime now = LocalDateTime.now(clock);
        return bookings.findByUserUsername(username, PageRequest.of(0, MINE_LIMIT, Sort.by(Sort.Order.desc("slotStart"), Sort.Order.desc("id"))))
            .stream().map(booking -> toResponse(booking, now, false)).toList();
    }

    @Transactional
    public TestRideResponse book(String username, TestRideCreateRequest request) {
        requireUsername(username);
        if (request == null || request.productId() == null || request.showroomId() == null || request.slotStart() == null || request.slotStart().isBlank()) {
            throw bad("Vui lòng chọn xe, cửa hàng và khung giờ.", "Please choose a bike, a showroom and a time.");
        }
        String phone = TestRideRules.normalizePhone(request.contactPhone());
        if (!TestRideRules.isValidPhone(phone)) {
            throw bad("Số điện thoại chưa hợp lệ. Vui lòng nhập số bắt đầu bằng 0 và gồm 10-11 chữ số.",
                "The phone number is not valid. Enter a number that starts with 0 and has 10 or 11 digits.");
        }
        String note = request.note() == null ? "" : request.note().trim();
        if (note.length() > MAX_NOTE) {
            throw bad("Ghi chú tối đa " + MAX_NOTE + " ký tự.", "A note can have at most " + MAX_NOTE + " characters.");
        }
        LocalDateTime slot;
        try {
            slot = LocalDateTime.parse(request.slotStart().trim());
        } catch (DateTimeParseException exception) {
            throw bad("Khung giờ không hợp lệ.", "The time is not valid.");
        }
        // The showroom row is locked first and on purpose before any plain SELECT: under REPEATABLE READ the first plain SELECT fixes the
        // snapshot, and a snapshot taken before the lock would not show a booking that another transaction committed while we waited.
        Showroom showroom = showrooms.findLockedById(request.showroomId())
            .orElseThrow(TestRideService::showroomNotFound);
        if (!Boolean.TRUE.equals(showroom.getActive())) {
            throw showroomNotFound();
        }
        // then the customer's own row, so two parallel requests of one customer (at two showrooms) cannot both pass the limit below; always showroom first, user second
        User user = users.findLockedByUsername(username).orElseThrow(TestRideService::signInRequired);
        Product product = products.findById(request.productId())
            .filter(found -> Boolean.TRUE.equals(found.getActive()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, RequestLanguage.text("Không tìm thấy xe này.", "This bike was not found.")));
        if (product.getSpecification() == null || !TestRideRules.isRideable(product.getSpecification().getVehicleType())) {
            throw bad("Sản phẩm này không phải xe để lái thử.", "This product is not a bike that can be test-ridden.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!TestRideRules.isOnGrid(slot, TestRideRules.parseOpeningHours(showroom.getOpeningHours()))) {
            throw bad("Khung giờ này nằm ngoài giờ mở cửa của cửa hàng.", "This time is outside the showroom's opening hours.");
        }
        if (!TestRideRules.isWithinWindow(slot, now, daysAhead)) {
            throw bad("Chỉ đặt lịch trong " + daysAhead + " ngày tới.", "You can book only within the next " + daysAhead + " days.");
        }
        if (slot.isBefore(now)) {
            throw bad("Khung giờ này đã qua.", "This time has already passed.");
        }
        if (!TestRideRules.isFarEnough(slot, now, leadMinutes)) {
            throw bad("Vui lòng đặt trước ít nhất " + leadText(true) + ".", "Please book at least " + leadText(false) + " ahead.");
        }
        if (bookings.existsByUserIdAndSlotStartAndStatusIn(user.getId(), slot, TestRideRules.OPEN)) {
            throw conflict("Bạn đã có một lịch lái thử vào giờ này.", "You already have a test ride at this time.");
        }
        if (bookings.countByUserIdAndStatusInAndSlotStartGreaterThanEqual(user.getId(), TestRideRules.OPEN, now) >= maxOpenPerCustomer) {
            throw conflict("Bạn đang có tối đa " + maxOpenPerCustomer + " lịch lái thử chưa diễn ra. Vui lòng hủy bớt hoặc đợi sau buổi lái thử.",
                "You already have " + maxOpenPerCustomer + " upcoming test rides, the most allowed. Cancel one or wait until after a ride.");
        }
        if (bookings.countAt(showroom.getId(), slot, TestRideRules.OPEN) >= capacity) {
            throw conflict("Khung giờ này đã đủ chỗ. Vui lòng chọn giờ khác.", "This time is full. Please choose another one.");
        }
        TestRideBooking booking = new TestRideBooking();
        booking.setUser(user);
        booking.setProduct(product);
        booking.setShowroom(showroom);
        booking.setSlotStart(slot);
        booking.setContactPhone(phone);
        booking.setCustomerNote(note.isEmpty() ? null : note);
        try {
            return toResponse(bookings.saveAndFlush(booking), now, false);
        } catch (DataIntegrityViolationException exception) {
            // uk_test_ride_active_user_slot: the same customer, the same time, committed by a second request that slipped past the check above
            throw conflict("Bạn đã có một lịch lái thử vào giờ này.", "You already have a test ride at this time.");
        }
    }

    /** The customer withdraws their own open booking until its slot begins. */
    @Transactional
    public TestRideResponse cancelOwn(String username, Long id) {
        requireUsername(username);
        TestRideBooking booking = bookings.findLockedById(id)
            .filter(found -> found.getUser() != null && username.equals(found.getUser().getUsername()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, RequestLanguage.text("Không tìm thấy lịch lái thử này.", "This test ride was not found.")));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!TestRideRules.canCustomerCancel(booking.getStatus(), booking.getSlotStart(), now)) {
            throw conflict("Lịch này không thể hủy nữa.", "This test ride can no longer be cancelled.");
        }
        booking.setStatus(TestRideBooking.CANCELLED);
        return toResponse(bookings.save(booking), now, false);
    }

    /**
     * The staff schedule. With no status it lists the bookings still open (requested or confirmed), soonest first, so an unconfirmed request and an
     * unclosed past ride stay on top until handled; {@code ALL} lists every status and a closed status lists only that one, latest first when no date
     * is given. A date narrows the list to that day (soonest first), a showroom to that showroom. At most {@link #SCHEDULE_LIMIT} rows.
     */
    @Transactional(readOnly = true)
    public List<TestRideResponse> schedule(String date, Long showroomId, String status) {
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (filter != null && !ALL.equals(filter) && !TestRideRules.isKnownStatus(filter)) {
            throw bad("Trạng thái không hợp lệ.", "Invalid status.");
        }
        boolean hasDate = date != null && !date.isBlank();
        LocalDateTime from = FAR_PAST;
        LocalDateTime to = FAR_FUTURE;
        if (hasDate) {
            LocalDate day = parseDate(date);
            from = day.atStartOfDay();
            to = day.plusDays(1).atStartOfDay();
        }
        Collection<String> statuses = filter == null ? TestRideRules.OPEN : ALL.equals(filter) ? TestRideRules.ALL : Set.of(filter);
        boolean soonestFirst = hasDate || TestRideRules.OPEN.containsAll(statuses);
        Sort sort = soonestFirst ? Sort.by(Sort.Order.asc("slotStart"), Sort.Order.asc("id")) : Sort.by(Sort.Order.desc("slotStart"), Sort.Order.desc("id"));
        LocalDateTime now = LocalDateTime.now(clock);
        return bookings.findSchedule(from, to, statuses, showroomId == null ? 0L : showroomId, PageRequest.of(0, SCHEDULE_LIMIT, sort))
            .stream().map(booking -> toResponse(booking, now, true)).toList();
    }

    /** How many requests wait for a confirmation and how many confirmed rides are past their time and still not closed (the list shows at most {@link #SCHEDULE_LIMIT}). */
    @Transactional(readOnly = true)
    public TestRideBacklogResponse backlog() {
        return new TestRideBacklogResponse(
            bookings.countByStatus(TestRideBooking.REQUESTED),
            bookings.countByStatusAndSlotStartLessThanEqual(TestRideBooking.CONFIRMED, LocalDateTime.now(clock)));
    }

    /** The staff move a booking to its next step, or change only the note the customer reads. */
    @Transactional
    public TestRideResponse update(Long id, TestRideUpdateRequest request) {
        TestRideBooking booking = bookings.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, RequestLanguage.text("Không tìm thấy lịch lái thử.", "Test ride not found.")));
        LocalDateTime now = LocalDateTime.now(clock);
        String previous = booking.getStatus();
        String previousNote = booking.getStaffNote();
        boolean closed = !TestRideRules.OPEN.contains(previous);
        if (request != null && request.staffNote() != null) {
            if (closed) {
                throw bad("Lịch đã đóng, không thể sửa ghi chú.", "This test ride is closed; its note can no longer be edited.");
            }
            String note = request.staffNote().trim();
            if (note.length() > MAX_STAFF_NOTE) {
                throw bad("Ghi chú tối đa " + MAX_STAFF_NOTE + " ký tự.", "A note can have at most " + MAX_STAFF_NOTE + " characters.");
            }
            booking.setStaffNote(note.isEmpty() ? null : note);
        }
        String next = null;
        if (request != null && request.status() != null && !request.status().isBlank()) {
            next = request.status().trim().toUpperCase(Locale.ROOT);
            if (!TestRideRules.isKnownStatus(next)) {
                throw bad("Trạng thái không hợp lệ.", "Invalid status.");
            }
            if (!TestRideRules.canMove(previous, next)) {
                throw bad("Không thể chuyển lịch từ bước hiện tại sang bước này.", "The test ride cannot move from its current step to this one.");
            }
            if (!TestRideRules.timingAllows(next, booking.getSlotStart(), now)) {
                throw bad(timingRefusalVi(next), timingRefusalEn(next));
            }
            // the reason must come with this very request: an older note (written at confirmation) is not a reason for the cancellation
            if (TestRideBooking.CANCELLED.equals(next) && (request.staffNote() == null || request.staffNote().isBlank())) {
                throw bad("Vui lòng ghi lý do khi hủy lịch.", "Please give a reason when cancelling a test ride.");
            }
            booking.setStatus(next);
            if (TestRideBooking.CONFIRMED.equals(next)) {
                booking.setConfirmedAt(now);
                if (!booking.getSlotStart().isAfter(now.plusHours(reminderHours))) {
                    // the ride is already inside the reminder window: the confirmation says the date, a reminder minutes later would be noise
                    booking.setReminderSentAt(now);
                }
            }
        }
        boolean statusChanged = next != null && !previous.equals(next);
        boolean changed = statusChanged || !Objects.equals(previousNote, booking.getStaffNote());
        TestRideBooking saved = bookings.save(booking);
        if (changed) {
            auditLog.record("xử lý lịch lái thử", "#" + saved.getId() + (statusChanged ? ": " + previous + " → " + saved.getStatus() : ": cập nhật ghi chú (" + saved.getStatus() + ")"));
        }
        if (statusChanged) {
            String kind = switch (saved.getStatus()) {
                case TestRideBooking.CONFIRMED -> TestRideMessages.CONFIRMED;
                case TestRideBooking.CANCELLED -> TestRideMessages.CANCELLED;
                case TestRideBooking.NO_SHOW -> TestRideMessages.NO_SHOW;
                default -> null; // done: nothing to tell
            };
            if (kind != null) {
                NotificationEventBridge.publish(noticeOf(saved, kind));
            }
        }
        return toResponse(saved, now, true);
    }

    static TestRideNoticeEvent noticeOf(TestRideBooking booking, String kind) {
        return new TestRideNoticeEvent(
            booking.getUser().getId(), kind, booking.getProduct().getName(), booking.getShowroom().getName(),
            booking.getShowroom().getAddress(), booking.getSlotStart(), booking.getStaffNote());
    }

    private TestRideResponse toResponse(TestRideBooking booking, LocalDateTime now, boolean forStaff) {
        User user = booking.getUser();
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        Showroom showroom = booking.getShowroom();
        Product product = booking.getProduct();
        return new TestRideResponse(
            booking.getId(),
            product.getId(),
            product.getName(),
            product.getSlug(),
            showroom.getId(),
            showroom.getName(),
            showroom.getAddress(),
            booking.getSlotStart(),
            booking.getStatus(),
            booking.getContactPhone(),
            booking.getCustomerNote(),
            booking.getStaffNote(),
            name.isEmpty() ? user.getEmail() : name,
            user.getEmail(),
            !forStaff && TestRideRules.canCustomerCancel(booking.getStatus(), booking.getSlotStart(), now),
            forStaff ? TestRideRules.nextSteps(booking.getStatus(), booking.getSlotStart(), now) : List.of(),
            booking.getCreatedAt());
    }

    private String leadText(boolean vietnamese) {
        if (leadMinutes % 60 == 0) {
            int hours = leadMinutes / 60;
            return vietnamese ? hours + " giờ" : hours + (hours == 1 ? " hour" : " hours");
        }
        return vietnamese ? leadMinutes + " phút" : leadMinutes + " minutes";
    }

    private static String timingRefusalVi(String next) {
        if (TestRideBooking.CONFIRMED.equals(next)) {
            return "Giờ hẹn đã bắt đầu, không thể xác nhận nữa. Hãy hủy lịch.";
        }
        if (TestRideBooking.DONE.equals(next)) {
            return "Chưa đến giờ lái thử, chưa thể đánh dấu đã lái thử.";
        }
        return "Chưa đến giờ hẹn, chưa thể đánh dấu khách không đến.";
    }

    private static String timingRefusalEn(String next) {
        if (TestRideBooking.CONFIRMED.equals(next)) {
            return "The slot has already begun, so it can no longer be confirmed. Cancel it instead.";
        }
        if (TestRideBooking.DONE.equals(next)) {
            return "The ride has not started yet, so it cannot be marked done.";
        }
        return "The slot has not begun yet, so the customer cannot be marked absent.";
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw bad("Vui lòng chọn ngày.", "Please choose a date.");
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException exception) {
            throw bad("Ngày không hợp lệ.", "The date is not valid.");
        }
    }

    private static LocalDateTime asDateTime(Object value) {
        return value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
    }

    private void requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw signInRequired();
        }
    }

    private static ResponseStatusException signInRequired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, RequestLanguage.text("Vui lòng đăng nhập để tiếp tục.", "Please sign in to continue."));
    }

    private static ResponseStatusException showroomNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, RequestLanguage.text("Không tìm thấy cửa hàng này.", "This showroom was not found."));
    }

    private static ResponseStatusException conflict(String vietnamese, String english) {
        return new ResponseStatusException(HttpStatus.CONFLICT, RequestLanguage.text(vietnamese, english));
    }

    private static ResponseStatusException bad(String vietnamese, String english) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, RequestLanguage.text(vietnamese, english));
    }
}
