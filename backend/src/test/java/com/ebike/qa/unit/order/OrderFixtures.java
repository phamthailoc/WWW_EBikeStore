package com.ebike.qa.unit.order;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.VehicleType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

/** Plain object builders shared by the order unit tests: no Spring, no database. */
final class OrderFixtures {

    static final String VIEW_ALL = "order:view-all";
    static final String UPDATE_STATUS = "order:update-status";
    static final String PAYMENT_VERIFY = "payment:verify";
    static final String VIEW_OWN = "order:view-own";
    static final String CANCEL_OWN = "order:cancel-own";

    private OrderFixtures() {
    }

    static User user(long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    static Authentication auth(String name, String... authorities) {
        Authentication authentication = new TestingAuthenticationToken(name, "n/a", authorities);
        authentication.setAuthenticated(true);
        return authentication;
    }

    static Authentication customer(String name) {
        return auth(name, "ROLE_CUSTOMER", VIEW_OWN, CANCEL_OWN);
    }

    static Authentication staff(String name) {
        return auth(name, "ROLE_MANAGER", VIEW_ALL, UPDATE_STATUS, PAYMENT_VERIFY);
    }

    static Product bike(long id, String name, String price, int stock, VehicleType type) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSlug("slug-" + id);
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        product.setActive(true);
        ProductSpecification specification = new ProductSpecification();
        specification.setVehicleType(type);
        product.setSpecification(specification);
        return product;
    }

    /** A motorbike-class vehicle: it must be registered and plated. */
    static Product motorbike(long id, String price, int stock) {
        return bike(id, "Moto " + id, price, stock, VehicleType.E_MOTORBIKE);
    }

    static Product accessory(long id, String price, int stock) {
        return bike(id, "Accessory " + id, price, stock, VehicleType.ACCESSORY);
    }

    static ProductVariant colour(long id, Product product, String name, int stock, String extra) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setColorName(name);
        variant.setStockQuantity(stock);
        variant.setAdditionalPrice(extra == null ? null : new BigDecimal(extra));
        variant.setActive(true);
        product.getVariants().add(variant);
        return variant;
    }

    static Showroom showroom(long id, String city, String district, boolean active) {
        Showroom showroom = new Showroom();
        showroom.setId(id);
        showroom.setName("Showroom " + id);
        showroom.setCity(city);
        showroom.setDistrict(district);
        showroom.setAddress("1 Main Street");
        showroom.setActive(active);
        return showroom;
    }

    static Order order(long id, String number, OrderStatus status, User owner) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber(number);
        order.setStatus(status);
        order.setUser(owner);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.of(2026, 1, 1, 10, 0));
        return order;
    }

    static OrderItem line(Order order, long id, Product product, ProductVariant variant, int quantity, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrder(order);
        item.setProduct(product);
        item.setProductVariant(variant);
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setLineTotal(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)));
        order.getItems().add(item);
        return item;
    }

    static Payment payment(Order order, PaymentMethod method, PaymentStatus status, String amount) {
        Payment payment = new Payment();
        payment.setId(order.getId() == null ? 1L : order.getId() + 5000);
        payment.setOrder(order);
        payment.setPaymentMethod(method);
        payment.setPaymentStatus(status);
        payment.setAmount(amount == null ? null : new BigDecimal(amount));
        payment.setCurrency("VND");
        order.setPayment(payment);
        return payment;
    }

    static Shipment shipment(Order order, Showroom pickup) {
        Shipment shipment = new Shipment();
        shipment.setId(order.getId() == null ? 1L : order.getId() + 7000);
        shipment.setOrder(order);
        shipment.setPickupShowroom(pickup);
        shipment.setRecipientName("Nguyen Van A");
        shipment.setPhoneNumber("0901234567");
        order.setShipment(shipment);
        return shipment;
    }

    static OrderItemUnit unit(OrderItem item, long id, String frame, String engine) {
        OrderItemUnit unit = new OrderItemUnit();
        ReflectionTestUtils.setField(unit, "id", id);
        unit.setOrderItem(item);
        unit.setFrameNumber(frame);
        unit.setEngineNumber(engine);
        item.getUnits().add(unit);
        return unit;
    }
}
