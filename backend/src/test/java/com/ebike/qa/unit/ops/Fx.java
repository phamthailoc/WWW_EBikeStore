package com.ebike.qa.unit.ops;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.warranty.WarrantyClaim;
import java.time.LocalDateTime;
import java.util.HashSet;
import org.springframework.test.util.ReflectionTestUtils;

/** Tiny object builders for the ops unit tests (no persistence). */
final class Fx {

    private Fx() {
    }

    /** Routes NotificationEventBridge.publish into the returned list; call release() in @AfterEach. */
    static java.util.List<Object> captureEvents() {
        java.util.List<Object> events = new java.util.ArrayList<>();
        new com.ebike.listener.notification.NotificationEventBridge()
            .setApplicationEventPublisher(new org.springframework.context.ApplicationEventPublisher() {
                @Override
                public void publishEvent(Object event) {
                    events.add(event);
                }
            });
        return events;
    }

    static void release() {
        new com.ebike.listener.notification.NotificationEventBridge().setApplicationEventPublisher(null);
    }

    static User user(long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setActive(true);
        u.setAccountStatus("ACTIVE");
        return u;
    }

    static Product product(long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setSlug(name.toLowerCase().replace(' ', '-'));
        p.setStockQuantity(stock);
        p.setActive(true);
        p.setVariants(new HashSet<>());
        return p;
    }

    static ProductVariant variant(Product p, long id, String colour, int stock, boolean active) {
        ProductVariant v = new ProductVariant();
        v.setId(id);
        v.setProduct(p);
        v.setColorName(colour);
        v.setStockQuantity(stock);
        v.setActive(active);
        p.getVariants().add(v);
        return v;
    }

    /** A delivered unit: order DELIVERED at {@code deliveredAt} (stored on the order). */
    static OrderItemUnit unit(long id, User owner, LocalDateTime deliveredAt, Integer unitMonths, Integer specMonths) {
        Product p = product(50 + id, "Bike " + id, 5);
        if (specMonths != null) {
            ProductSpecification spec = new ProductSpecification();
            spec.setWarrantyMonths(specMonths);
            p.setSpecification(spec);
        }
        Order order = new Order();
        order.setId(900 + id);
        order.setOrderNumber("ORD-" + id);
        order.setUser(owner);
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(deliveredAt);
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.of(2020, 1, 1, 0, 0));
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(p);
        item.setProductName(p.getName());
        OrderItemUnit unit = new OrderItemUnit();
        ReflectionTestUtils.setField(unit, "id", id);
        unit.setOrderItem(item);
        unit.setFrameNumber("FRAME" + id);
        unit.setWarrantyMonths(unitMonths);
        return unit;
    }

    static Shipment shipment(LocalDateTime deliveredAt) {
        Shipment s = new Shipment();
        s.setDeliveredAt(deliveredAt);
        s.setPhoneNumber("0900000000");
        return s;
    }

    static WarrantyClaim claim(long id, OrderItemUnit unit, User user, String status, String note) {
        WarrantyClaim c = new WarrantyClaim();
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "createdAt", LocalDateTime.of(2025, 1, 1, 0, 0));
        ReflectionTestUtils.setField(c, "updatedAt", LocalDateTime.of(2025, 1, 1, 0, 0));
        c.setUnit(unit);
        c.setUser(user);
        c.setStatus(status);
        c.setStaffNote(note);
        c.setDescription("description long enough");
        return c;
    }
}
