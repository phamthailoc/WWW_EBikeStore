package com.ebike.service.testride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.dto.testride.TestRideCreateRequest;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.service.admin.AuditLogService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * V23 against a real MariaDB: the JPQL of the repository, the generated unique key, the lock order of booking, and two requests racing for the last place.
 * It runs only when {@code -Dtestride.db.url=jdbc:mariadb://host:port/ebike_db} names a database built from {@code setup_ebike_database.sql}
 * ({@code -Dtestride.db.user} and {@code -Dtestride.db.password} when it is not an empty-password root). Hibernate validates the whole schema on start,
 * so the run also shows that the entities and the script agree. Every row it adds is removed again; nothing else is touched.
 */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "app.test-ride.capacity-per-slot=1",
    "app.test-ride.lead-minutes=0",
    "app.test-ride.max-open-per-customer=5"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "testride.db.url", matches = ".+")
@Import(TestRideService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TestRideDatabaseTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("testride.db.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("testride.db.user", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("testride.db.password", ""));
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestRideBookingRepository bookings;
    @Autowired private TestRideService service;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private AuditLogService auditLog;

    private final List<Long> users = new ArrayList<>();
    private final List<Long> showrooms = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long showroom : showrooms) {
            jdbc.update("delete from test_ride_bookings where showroom_id = ?", showroom);
        }
        for (Long user : users) {
            jdbc.update("delete from test_ride_bookings where user_id = ?", user);
            jdbc.update("delete from users where id = ?", user);
        }
        for (Long showroom : showrooms) {
            jdbc.update("delete from showrooms where id = ?", showroom);
        }
    }

    // ---- helpers ----

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private long newUser() {
        String name = "trdb-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("insert into users (username, email, password_hash, first_name, last_name) values (?, ?, 'x', 'Test', 'Rider')", name, name + "@example.test");
        long id = jdbc.queryForObject("select id from users where username = ?", Long.class, name);
        users.add(id);
        return id;
    }

    private String usernameOf(long id) {
        return jdbc.queryForObject("select username from users where id = ?", String.class, id);
    }

    private long newShowroom(boolean active) {
        jdbc.update("insert into showrooms (name, district, address, opening_hours, is_active) values (?, 'Test', 'Test street', '08:00 - 21:00', ?)",
            "TRDB " + UUID.randomUUID().toString().substring(0, 8), active);
        long id = jdbc.queryForObject("select max(id) from showrooms", Long.class);
        showrooms.add(id);
        return id;
    }

    private long bikeId() {
        return jdbc.queryForObject("select p.id from products p join product_specifications s on s.product_id = p.id "
            + "where p.is_active = 1 and s.vehicle_type not in ('BATTERY', 'ACCESSORY') order by p.id limit 1", Long.class);
    }

    private long insertBooking(long user, long showroom, LocalDateTime slot, String status, LocalDateTime cancelledByCustomerAt) {
        jdbc.update("insert into test_ride_bookings (user_id, product_id, showroom_id, slot_start, status, contact_phone, customer_note, staff_note, cancelled_by_customer_at) "
                + "values (?, ?, ?, ?, ?, '0901234567', 'customer words', 'shop words', ?)",
            user, bikeId(), showroom, Timestamp.valueOf(slot), status, cancelledByCustomerAt == null ? null : Timestamp.valueOf(cancelledByCustomerAt));
        return jdbc.queryForObject("select max(id) from test_ride_bookings", Long.class);
    }

    private static LocalDateTime tomorrowAt(int hour) {
        return LocalDate.now(ZONE).plusDays(1).atTime(hour, 0);
    }

    private TestRideCreateRequest request(long showroom, LocalDateTime slot) {
        return new TestRideCreateRequest(bikeId(), showroom, slot.toString(), "0901234567", null);
    }

    /** Runs the tasks at the same moment and returns the status each ended with: 201 for a booking, the HTTP status of its refusal, or -1 for anything else. */
    private List<Integer> race(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    task.call();
                    return 201;
                } catch (ResponseStatusException refused) {
                    return refused.getStatusCode().value();
                } catch (Exception other) {
                    other.printStackTrace();
                    return -1;
                }
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(60, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
        return statuses;
    }

    // ---- repository queries ----

    @Test
    void scheduleFiltersByStatusDayAndShowroom() {
        long user = newUser();
        long a = newShowroom(true);
        long b = newShowroom(true);
        LocalDateTime day = LocalDate.of(2090, 3, 4).atTime(9, 0);
        long open = insertBooking(user, a, day, "REQUESTED", null);
        long done = insertBooking(user, a, day.plusHours(2), "DONE", null);
        long otherShowroom = insertBooking(user, b, day.plusHours(4), "CONFIRMED", null);
        long nextDay = insertBooking(user, a, day.plusDays(1), "CONFIRMED", null);
        LocalDateTime from = day.toLocalDate().atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        Sort soonest = Sort.by(Sort.Order.asc("slotStart"), Sort.Order.asc("id"));

        // every showroom (0), open bookings of that one day
        List<Long> all = tx().execute(s -> ids(bookings.findSchedule(from, to, TestRideRules.OPEN, 0L, PageRequest.of(0, 50, soonest))));
        assertTrue(all.containsAll(List.of(open, otherShowroom)), all.toString());
        assertFalse(all.contains(done));
        assertFalse(all.contains(nextDay), "the window is half open");
        // one showroom
        assertEquals(List.of(open), tx().execute(s -> ids(bookings.findSchedule(from, to, TestRideRules.OPEN, a, PageRequest.of(0, 50, soonest)))));
        // every status, the page limit applies
        assertEquals(List.of(open, done), tx().execute(s -> ids(bookings.findSchedule(from, to, TestRideRules.ALL, a, PageRequest.of(0, 2, soonest)))));
        // the rows come with what the response reads, outside any transaction
        TestRideBooking first = bookings.findSchedule(from, to, TestRideRules.OPEN, a, PageRequest.of(0, 5, soonest)).get(0);
        assertNotNull(first.getUser().getUsername());
        assertNotNull(first.getProduct().getName());
        assertNotNull(first.getShowroom().getName());
    }

    private static List<Long> ids(List<TestRideBooking> found) {
        return found.stream().map(TestRideBooking::getId).toList();
    }

    @Test
    void countsPerSlotAndAtOneSlot() {
        long user = newUser();
        long other = newUser();
        long room = newShowroom(true);
        LocalDateTime nine = LocalDate.of(2090, 3, 5).atTime(9, 0);
        insertBooking(user, room, nine, "REQUESTED", null);
        insertBooking(other, room, nine, "CONFIRMED", null);
        insertBooking(user, room, nine.plusHours(2), "CANCELLED", null);
        insertBooking(other, room, nine.plusHours(3), "DONE", null);

        List<Object[]> rows = bookings.countBySlot(room, nine.toLocalDate().atStartOfDay(), nine.toLocalDate().plusDays(1).atStartOfDay(), TestRideRules.OPEN);
        assertEquals(1, rows.size());
        // whatever type the driver hands back for the slot, the service reads it
        Object slot = rows.get(0)[0];
        assertEquals(nine, slot instanceof Timestamp stamp ? stamp.toLocalDateTime() : (LocalDateTime) slot);
        assertEquals(2L, ((Number) rows.get(0)[1]).longValue());
        assertEquals(2L, bookings.countAt(room, nine, TestRideRules.OPEN));
        assertEquals(0L, bookings.countAt(room, nine.plusHours(2), TestRideRules.OPEN));
    }

    @Test
    void backlogCounts() {
        long user = newUser();
        long live = newShowroom(true);
        long off = newShowroom(false);
        LocalDateTime now = LocalDateTime.now(ZONE).withNano(0);
        long before = bookings.countOpenUnavailable(TestRideRules.OPEN, now.minusHours(1));
        long pendingBefore = bookings.countRequestedAvailable(now);
        long withdrawnBefore = bookings.countCancelledByCustomerBetween(now, now.plusHours(24));
        insertBooking(user, off, now.plusDays(3), "REQUESTED", null);
        insertBooking(user, off, now.plusDays(4), "CONFIRMED", null);
        insertBooking(user, off, now.plusDays(5), "CANCELLED", null);
        insertBooking(user, live, now.plusDays(6), "REQUESTED", null);
        insertBooking(user, live, now.plusHours(5), "CANCELLED", now.minusHours(1));
        insertBooking(user, live, now.plusHours(30), "CANCELLED", now.minusHours(1));

        assertEquals(before + 2, bookings.countOpenUnavailable(TestRideRules.OPEN, now.minusHours(1)));
        // the request at the switched-off showroom is counted as unavailable, not as waiting: no booking is counted twice
        assertEquals(pendingBefore + 1, bookings.countRequestedAvailable(now));
        // a request whose start time has come can no longer be confirmed, so it does not wait
        insertBooking(user, live, now.minusMinutes(10), "REQUESTED", null);
        assertEquals(pendingBefore + 1, bookings.countRequestedAvailable(now));
        // a booking whose hour is over is no longer asked to be cancelled
        insertBooking(user, off, now.minusHours(3), "CONFIRMED", null);
        assertEquals(before + 2, bookings.countOpenUnavailable(TestRideRules.OPEN, now.minusHours(1)));
        assertEquals(withdrawnBefore + 1, bookings.countCancelledByCustomerBetween(now, now.plusHours(24)));
    }

    @Test
    void erasureFreesThePlacesAndDropsThePersonalData() {
        long user = newUser();
        long bystander = newUser();
        long room = newShowroom(true);
        LocalDateTime base = LocalDate.of(2090, 4, 1).atTime(9, 0);
        long requested = insertBooking(user, room, base, "REQUESTED", null);
        long confirmed = insertBooking(user, room, base.plusHours(2), "CONFIRMED", null);
        long done = insertBooking(user, room, base.plusHours(4), "DONE", null);
        long untouched = insertBooking(bystander, room, base, "CONFIRMED", null);
        LocalDateTime now = LocalDateTime.now(ZONE).withNano(0);

        tx().executeWithoutResult(s -> {
            assertEquals(3, bookings.clearPersonalDataOf(user, now));
            assertEquals(2, bookings.cancelOpenOf(user, now));
        });

        assertEquals("CANCELLED", statusOf(requested));
        assertEquals("CANCELLED", statusOf(confirmed));
        // the reason is a code; the backlog hears of the ride the staff had confirmed, not of the request nobody had
        assertEquals("ERASED", jdbc.queryForObject("select cancel_source from test_ride_bookings where id = ?", String.class, requested));
        assertEquals("ERASED", jdbc.queryForObject("select cancel_source from test_ride_bookings where id = ?", String.class, confirmed));
        assertNull(jdbc.queryForObject("select cancelled_by_customer_at from test_ride_bookings where id = ?", Timestamp.class, requested));
        assertNotNull(jdbc.queryForObject("select cancelled_by_customer_at from test_ride_bookings where id = ?", Timestamp.class, confirmed));
        assertNull(jdbc.queryForObject("select cancel_source from test_ride_bookings where id = ?", String.class, done));
        assertEquals("DONE", statusOf(done));
        for (long id : List.of(requested, confirmed, done)) {
            assertEquals("", jdbc.queryForObject("select contact_phone from test_ride_bookings where id = ?", String.class, id));
            assertNull(jdbc.queryForObject("select customer_note from test_ride_bookings where id = ?", String.class, id));
        }
        assertEquals("CONFIRMED", statusOf(untouched));
        assertEquals("0901234567", jdbc.queryForObject("select contact_phone from test_ride_bookings where id = ?", String.class, untouched));
        // the places are free for somebody else
        assertEquals(1L, bookings.countAt(room, base, TestRideRules.OPEN));
    }

    private String statusOf(long id) {
        return jdbc.queryForObject("select status from test_ride_bookings where id = ?", String.class, id);
    }

    // ---- the generated unique key ----

    @Test
    void theDatabaseRefusesTwoOpenBookingsOfOneCustomerAtOneTime() {
        long user = newUser();
        long a = newShowroom(true);
        long b = newShowroom(true);
        LocalDateTime slot = LocalDate.of(2090, 5, 1).atTime(9, 0);
        long first = insertBooking(user, a, slot, "REQUESTED", null);
        DataIntegrityViolationException refused = null;
        try {
            insertBooking(user, b, slot, "CONFIRMED", null);
        } catch (DataIntegrityViolationException expected) {
            refused = expected;
        }
        assertNotNull(refused);
        assertTrue(String.valueOf(refused.getMostSpecificCause().getMessage()).contains(TestRideService.ACTIVE_USER_SLOT_KEY), refused.getMostSpecificCause().getMessage());
        // a closed booking does not hold the time: the same customer may take it again
        jdbc.update("update test_ride_bookings set status = 'CANCELLED' where id = ?", first);
        insertBooking(user, b, slot, "REQUESTED", null);
        // and two closed ones at the same time do not collide either
        insertBooking(user, a, slot, "CANCELLED", null);
        insertBooking(user, a, slot, "DONE", null);
    }

    @Test
    void deletingTheUserRowStillCascadesWithTheGeneratedColumn() {
        long user = newUser();
        long room = newShowroom(true);
        insertBooking(user, room, LocalDate.of(2090, 5, 2).atTime(9, 0), "REQUESTED", null);
        jdbc.update("delete from users where id = ?", user);
        assertEquals(0, jdbc.queryForObject("select count(*) from test_ride_bookings where user_id = ?", Integer.class, user));
        users.remove(Long.valueOf(user));
    }

    // ---- the lock order of booking() and the race for the last place ----

    @Test
    void twoCustomersRacingForTheLastPlaceLeaveExactlyOneBooking() throws Exception {
        long room = newShowroom(true);
        LocalDateTime slot = tomorrowAt(10);
        for (int round = 0; round < 5; round += 1) {
            long a = newUser();
            long b = newUser();
            List<Integer> statuses = race(List.of(
                () -> service.book(usernameOf(a), request(room, slot)),
                () -> service.book(usernameOf(b), request(room, slot))));
            assertEquals(1, statuses.stream().filter(code -> code == 201).count(), "round " + round + ": " + statuses);
            assertEquals(1, statuses.stream().filter(code -> code == 409).count(), "round " + round + ": " + statuses);
            assertEquals(1L, bookings.countAt(room, slot, TestRideRules.OPEN));
            jdbc.update("delete from test_ride_bookings where showroom_id = ?", room);
        }
    }

    @Test
    void oneCustomerAtTwoShowroomsAtTheSameTimeGetsOneBookingAndNoDeadlock() throws Exception {
        long a = newShowroom(true);
        long b = newShowroom(true);
        LocalDateTime slot = tomorrowAt(11);
        for (int round = 0; round < 5; round += 1) {
            long user = newUser();
            String name = usernameOf(user);
            List<Integer> statuses = race(List.of(
                () -> service.book(name, request(a, slot)),
                () -> service.book(name, request(b, slot)),
                () -> service.book(name, request(a, slot))));
            assertEquals(1, statuses.stream().filter(code -> code == 201).count(), "round " + round + ": " + statuses);
            assertTrue(statuses.stream().noneMatch(code -> code == -1), "no request may fail with anything but a clear refusal: " + statuses);
            assertEquals(1, jdbc.queryForObject("select count(*) from test_ride_bookings where user_id = ? and status in ('REQUESTED','CONFIRMED')", Integer.class, user));
            // the places are taken (the capacity is 1): empty both showrooms so the next round starts free
            jdbc.update("delete from test_ride_bookings where showroom_id in (?, ?)", a, b);
        }
    }

    @Test
    void aBookingAndTheErasureOfTheSameAccountDoNotDeadlock() throws Exception {
        long room = newShowroom(true);
        LocalDateTime slot = tomorrowAt(12);
        for (int round = 0; round < 5; round += 1) {
            long user = newUser();
            String name = usernameOf(user);
            // the erasure as AccountErasure does it: the account row first, then the bookings
            List<Integer> statuses = race(List.of(
                () -> service.book(name, request(room, slot)),
                () -> tx().execute(s -> {
                    jdbc.queryForObject("select id from users where id = ? for update", Long.class, user);
                    bookings.clearPersonalDataOf(user, LocalDateTime.now(ZONE));
                    return bookings.cancelOpenOf(user, LocalDateTime.now(ZONE));
                })));
            assertTrue(statuses.stream().noneMatch(code -> code == -1), "round " + round + ": " + statuses);
            jdbc.update("delete from test_ride_bookings where user_id = ?", user);
        }
    }
}
