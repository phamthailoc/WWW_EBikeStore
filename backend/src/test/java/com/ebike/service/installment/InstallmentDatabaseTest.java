package com.ebike.service.installment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.auth.UserRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V24 against a real MariaDB: the JPQL of the application repository (the counts, the sum, the open list, the expiry candidates with their left join, the
 * closing and erasing updates, the queue with its entity graph) and the order of the row locks. It runs only when
 * {@code -Dinstallment.db.url=jdbc:mariadb://host:port/ebike_db} names a database built from {@code setup_ebike_database.sql} (or with
 * {@code update_installments.sql} applied); {@code -Dinstallment.db.user} and {@code -Dinstallment.db.password} say who connects (an empty-password
 * root otherwise). Hibernate validates the whole schema on start, so the run also shows that the entity and the script agree. Every row the test adds is
 * removed again; nothing else is touched.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "installment.db.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstallmentDatabaseTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("installment.db.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("installment.db.user", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("installment.db.password", ""));
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private InstallmentApplicationRepository applications;
    @Autowired private OrderRepository orders;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<Long> users = new ArrayList<>();
    private final List<Long> orderIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // the orders go first: their payments and applications go with them
        for (Long order : orderIds) {
            jdbc.update("delete from orders where id = ?", order);
        }
        for (Long user : users) {
            jdbc.update("delete from users where id = ?", user);
        }
    }

    // ---- helpers ----

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private long newUser() {
        String name = "indb-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("insert into users (username, email, password_hash, first_name, last_name) values (?, ?, 'x', 'Test', 'Buyer')", name, name + "@example.test");
        long id = jdbc.queryForObject("select id from users where username = ?", Long.class, name);
        users.add(id);
        return id;
    }

    private long newOrder(long user, OrderStatus status, String paymentMethod) {
        String number = "INDB-" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("insert into orders (user_id, order_number, status, subtotal, total_amount) values (?, ?, ?, 30000000, 30000000)", user, number, status.name());
        long id = jdbc.queryForObject("select id from orders where order_number = ?", Long.class, number);
        orderIds.add(id);
        if (paymentMethod != null) {
            jdbc.update("insert into payments (order_id, payment_method, payment_status, amount) values (?, ?, 'PENDING', 9000000)", id, paymentMethod);
        }
        return id;
    }

    private long newApplication(long order, long user, String status, String monthlyPayment) {
        jdbc.update("insert into installment_applications (order_id, user_id, id_number_masked, monthly_income, order_total, down_payment_percent, down_payment_amount, "
                + "financed_amount, term_months, annual_rate_percent, monthly_payment, total_payable, status) "
                + "values (?, ?, '********6789', 20000000, 30000000, 30, 9000000, 21000000, 12, 10, ?, 32100000, ?)",
            order, user, new BigDecimal(monthlyPayment), status);
        return jdbc.queryForObject("select id from installment_applications where order_id = ?", Long.class, order);
    }

    private String statusOf(long application) {
        return jdbc.queryForObject("select status from installment_applications where id = ?", String.class, application);
    }

    // ---- repository queries ----

    @Test
    void theCountTheSumAndTheOpenListFollowTheStatusOfTheApplicationAndTheOrder() {
        long user = newUser();
        long waiting = newOrder(user, OrderStatus.PENDING, "INSTALLMENT");
        long approved = newOrder(user, OrderStatus.PENDING, "INSTALLMENT");
        long paid = newOrder(user, OrderStatus.CONFIRMED, "INSTALLMENT");
        long cancelled = newOrder(user, OrderStatus.CANCELLED, "INSTALLMENT");
        long a1 = newApplication(waiting, user, "PENDING", "1000000");
        long a2 = newApplication(approved, user, "APPROVED", "500000");
        newApplication(paid, user, "APPROVED", "300000");
        newApplication(cancelled, user, "CANCELLED", "700000");

        // open: still holding the order's stock (down payment not paid yet)
        assertEquals(2, applications.countOpenOf(user));
        // what the plans cost every month: pending and approved ones on orders that are not cancelled, the paid one too
        assertEquals(0, applications.sumMonthlyPaymentOf(user, java.time.LocalDateTime.now().minusMonths(12)).compareTo(new BigDecimal("1800000")));
        List<Long> open = tx().execute(s -> applications.findOpenOf(user).stream().map(InstallmentApplication::getId).toList());
        assertEquals(2, open.size());
        assertTrue(open.containsAll(List.of(a1, a2)));
        // a customer with nothing: a count of 0 and no sum at all
        long nobody = newUser();
        assertEquals(0, applications.countOpenOf(nobody));
        assertNull(applications.sumMonthlyPaymentOf(nobody, java.time.LocalDateTime.now().minusMonths(12)));
        // plans taken out longer ago than the longest term are paid off and no longer count
        assertNull(applications.sumMonthlyPaymentOf(user, java.time.LocalDateTime.now().plusDays(1)));
    }

    @Test
    void theExpiryCandidatesComeWithTheirPaymentWhenTheyHaveOne() {
        long user = newUser();
        long withPayment = newOrder(user, OrderStatus.PENDING, "INSTALLMENT");
        long withoutPayment = newOrder(user, OrderStatus.PENDING, null);
        long finished = newOrder(user, OrderStatus.CONFIRMED, "INSTALLMENT");
        List<OrderRepository.ExpiryCandidate> found = tx().execute(s -> orders.findExpiryCandidates(OrderStatus.PENDING, LocalDateTime.now().plusDays(1)));
        OrderRepository.ExpiryCandidate paid = found.stream().filter(c -> c.getId() == withPayment).findFirst().orElseThrow();
        OrderRepository.ExpiryCandidate unpaid = found.stream().filter(c -> c.getId() == withoutPayment).findFirst().orElseThrow();
        assertEquals(PaymentMethod.INSTALLMENT, paid.getPaymentMethod());
        assertNotNull(paid.getCreatedAt());
        assertNull(unpaid.getPaymentMethod(), "the left join keeps an order that has no payment row yet");
        assertTrue(found.stream().noneMatch(c -> c.getId() == finished), "only orders in the asked status");
    }

    @Test
    void closingAnOrdersApplicationKeepsARejectedVerdict() {
        long user = newUser();
        long open = newOrder(user, OrderStatus.PENDING, "INSTALLMENT");
        long rejected = newOrder(user, OrderStatus.CANCELLED, "INSTALLMENT");
        long openApplication = newApplication(open, user, "PENDING", "1000000");
        long rejectedApplication = newApplication(rejected, user, "REJECTED", "1000000");
        assertEquals(Integer.valueOf(1), tx().execute(s -> applications.cancelOpenOfOrder(open, LocalDateTime.now())));
        assertEquals(Integer.valueOf(0), tx().execute(s -> applications.cancelOpenOfOrder(rejected, LocalDateTime.now())));
        assertEquals("CANCELLED", statusOf(openApplication));
        assertEquals("REJECTED", statusOf(rejectedApplication));
    }

    @Test
    void erasingAnAccountClearsTheIdNumberAndTheIncomeButKeepsThePlan() {
        long user = newUser();
        long order = newOrder(user, OrderStatus.CANCELLED, "INSTALLMENT");
        long application = newApplication(order, user, "CANCELLED", "1000000");
        assertEquals(Integer.valueOf(1), tx().execute(s -> applications.clearPersonalDataOf(user, LocalDateTime.now())));
        assertNull(jdbc.queryForObject("select id_number_masked from installment_applications where id = ?", String.class, application));
        assertNull(jdbc.queryForObject("select monthly_income from installment_applications where id = ?", BigDecimal.class, application));
        assertEquals(0, jdbc.queryForObject("select monthly_payment from installment_applications where id = ?", BigDecimal.class, application)
            .compareTo(new BigDecimal("1000000")));
    }

    @Test
    void theQueueLoadsEverythingARowShowsOutsideATransaction() {
        long user = newUser();
        long reviewer = newUser();
        long order = newOrder(user, OrderStatus.PENDING, "INSTALLMENT");
        long application = newApplication(order, user, "PENDING", "1000000");
        jdbc.update("update installment_applications set reviewed_by = ?, reviewed_at = ? where id = ?", reviewer, Timestamp.valueOf(LocalDateTime.now()), application);
        List<InstallmentApplication> queue = applications.findByStatusOrderByCreatedAtAsc("PENDING", PageRequest.of(0, 200));
        InstallmentApplication mine = queue.stream().filter(a -> a.getId() == application).findFirst().orElseThrow();
        assertNotNull(mine.getUser().getUsername());
        assertNotNull(mine.getReviewedBy().getUsername());
        assertEquals(PaymentMethod.INSTALLMENT, mine.getOrder().getPayment().getPaymentMethod());
        assertTrue(applications.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 200)).stream().anyMatch(a -> a.getId() == application));
    }

    // ---- locks ----

    @Test
    void theRowLocksCanBeTakenInTheOrderEveryOperationUses() {
        long user = newUser();
        long order = newOrder(user, OrderStatus.PENDING, "INSTALLMENT");
        long application = newApplication(order, user, "PENDING", "1000000");
        // the account, then the order, then the application: the same order as an erasure, a rejection and the expiry job
        Boolean locked = tx().execute(s -> {
            userRepository.findLockedById(user).orElseThrow();
            Long found = applications.findOrderIdById(application).orElseThrow();
            orders.findLockedById(found).orElseThrow();
            return applications.findLockedById(application).isPresent();
        });
        assertEquals(Boolean.TRUE, locked);
    }
}
