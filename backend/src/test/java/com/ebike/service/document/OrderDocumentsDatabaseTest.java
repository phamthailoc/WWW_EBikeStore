package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.warranty.WarrantyRules;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * V27 against a real MariaDB: the entity graph of {@code OrderRepository.findForDocuments} (its attribute paths are only resolved when the query runs).
 * A delivered order with two lines and four numbered vehicles is read with no transaction around the call, so the order is detached afterwards, and then
 * everything the receipt and the warranty card print is read from it: a path missing from the graph would end in a LazyInitializationException here.
 * It runs only when {@code -Ddocuments.db.url=jdbc:mariadb://host:port/ebike_db} names a database built from {@code setup_ebike_database.sql};
 * {@code -Ddocuments.db.user} and {@code -Ddocuments.db.password} say who connects (an empty-password root otherwise). Every row the test adds is removed again.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "documents.db.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderDocumentsDatabaseTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("documents.db.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("documents.db.user", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("documents.db.password", ""));
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderRepository orders;

    private final String tag = UUID.randomUUID().toString().substring(0, 12);
    private Long userId;
    private Long categoryId;
    private Long productId;
    private Long variantId;
    private Long showroomId;
    private Long orderId;

    @AfterEach
    void cleanUp() {
        if (orderId != null) {
            jdbc.update("delete from order_item_units where order_item_id in (select id from order_items where order_id = ?)", orderId);
            jdbc.update("delete from order_items where order_id = ?", orderId);
            jdbc.update("delete from payments where order_id = ?", orderId);
            jdbc.update("delete from shipments where order_id = ?", orderId);
            jdbc.update("delete from orders where id = ?", orderId);
        }
        if (variantId != null) {
            jdbc.update("delete from product_variants where id = ?", variantId);
        }
        if (productId != null) {
            jdbc.update("delete from product_specifications where product_id = ?", productId);
            jdbc.update("delete from products where id = ?", productId);
        }
        if (categoryId != null) {
            jdbc.update("delete from categories where id = ?", categoryId);
        }
        if (showroomId != null) {
            jdbc.update("delete from showrooms where id = ?", showroomId);
        }
        if (userId != null) {
            jdbc.update("delete from users where id = ?", userId);
        }
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void aDeliveredOrderWithSeveralLinesAndVehiclesLoadsEverythingTheDocumentsPrint() {
        jdbc.update("insert into users (username, email, password_hash, first_name, last_name) values (?, ?, 'x', 'Test', 'Buyer')", "docdb-" + tag, "docdb-" + tag + "@example.test");
        userId = id("select id from users where username = ?", "docdb-" + tag);
        jdbc.update("insert into categories (name, slug) values (?, ?)", "docdb " + tag, "docdb-" + tag);
        categoryId = id("select id from categories where slug = ?", "docdb-" + tag);
        jdbc.update("insert into products (category_id, name, slug, price) values (?, ?, ?, 20000000)", categoryId, "Docdb bike " + tag, "docdb-bike-" + tag);
        productId = id("select id from products where slug = ?", "docdb-bike-" + tag);
        jdbc.update("insert into product_specifications (product_id, warranty_months) values (?, 24)", productId);
        jdbc.update("insert into product_variants (product_id, sku, variant_name, color_name) values (?, ?, 'Standard', 'Xanh')", productId, "DOCDB-" + tag);
        variantId = id("select id from product_variants where sku = ?", "DOCDB-" + tag);
        jdbc.update("insert into showrooms (name, district, address) values (?, 'Quận 7', '12 Nguyễn Văn Linh')", "Docdb showroom " + tag);
        showroomId = id("select id from showrooms where name = ?", "Docdb showroom " + tag);

        String number = "DOCDB-" + tag;
        jdbc.update("insert into orders (user_id, order_number, status, subtotal, total_amount, customer_email) values (?, ?, 'DELIVERED', 80000000, 80000000, ?)",
            userId, number, "docdb-" + tag + "@example.test");
        orderId = id("select id from orders where order_number = ?", number);
        jdbc.update("insert into payments (order_id, payment_method, payment_status, amount) values (?, 'PAY_LATER', 'PAID', 80000000)", orderId);
        jdbc.update("insert into shipments (order_id, shipment_status, recipient_name, phone_number, pickup_showroom_id) values (?, 'DELIVERED', 'Nguyễn Văn An', '0901234567', ?)",
            orderId, showroomId);
        for (int line = 1; line <= 2; line++) {
            jdbc.update("insert into order_items (order_id, product_id, product_variant_id, product_name, unit_price, quantity, line_total) values (?, ?, ?, 'Docdb bike', 20000000, 2, 40000000)",
                orderId, productId, variantId);
            long item = id("select max(id) from order_items where order_id = ?", orderId);
            for (int unit = 1; unit <= 2; unit++) {
                jdbc.update("insert into order_item_units (order_item_id, frame_number, engine_number, warranty_months) values (?, ?, ?, 24)",
                    item, "F" + tag + line + unit, "E" + tag + line + unit);
            }
        }

        // no transaction here: the order comes back detached, so every lazy association left out of the graph would fail below
        Order order = orders.findForDocuments(orderId).orElseThrow();

        assertEquals(2, order.getItems().size());
        List<OrderItemUnit> units = new ArrayList<>();
        for (OrderItem item : DocumentModels.sortedItems(order)) {
            assertNotNull(item.getProduct().getSpecification().getWarrantyMonths());
            assertNotNull(item.getProductVariant().getColorName());
            units.addAll(item.getUnits());
        }
        assertEquals(4, units.size());
        assertNotNull(order.getShipment().getPickupShowroom().getName());
        assertNotNull(order.getPayment().getPaymentMethod());
        assertNotNull(order.getUser().getUsername());

        LocalDateTime now = LocalDateTime.now();
        var store = DocumentFixtures.store();
        assertTrue(DocumentModels.receipt(order, null, store, now).containsKey("customer"));
        assertTrue(DocumentModels.warrantyCards(order, units, store, WarrantyRules.parseIntervals(WarrantyRules.DEFAULT_INTERVALS), now).containsKey("cards"));
    }
}
