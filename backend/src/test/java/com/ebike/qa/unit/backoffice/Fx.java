package com.ebike.qa.unit.backoffice;

import com.ebike.model.entity.auth.Permission;
import com.ebike.model.entity.auth.Role;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.ShipmentStatus;
import com.ebike.model.entity.product.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/** Builders shared by the back-office unit tests. */
final class Fx {

    private Fx() {
    }

    static Role role(String name, String... permissionCodes) {
        Role role = new Role();
        role.setName(name);
        Set<Permission> permissions = new HashSet<>();
        for (String code : permissionCodes) {
            Permission p = new Permission();
            p.setCode(code);
            permissions.add(p);
        }
        role.setPermissions(permissions);
        return role;
    }

    static User user(long id, String username, String email, String... roleNames) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hash:" + username);
        user.setActive(true);
        user.setVerified(true);
        user.setAccountStatus("ACTIVE");
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            roles.add(role(name));
        }
        user.setRoles(roles);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(id));
        ReflectionTestUtils.setField(user, "updatedAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        return user;
    }

    static Order order(long id, String number, OrderStatus status, LocalDateTime createdAt) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber(number);
        order.setStatus(status);
        order.setSubtotal(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        ReflectionTestUtils.setField(order, "updatedAt", createdAt);
        return order;
    }

    static Payment payment(long id, Order order, PaymentMethod method, PaymentStatus status, String amount, LocalDateTime paidAt) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setOrder(order);
        payment.setPaymentMethod(method);
        payment.setPaymentStatus(status);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrency("VND");
        payment.setPaidAt(paidAt);
        order.setPayment(payment);
        return payment;
    }

    static Shipment shipment(Order order, ShipmentStatus status) {
        Shipment shipment = new Shipment();
        shipment.setId(order.getId() == null ? 1L : order.getId() + 1000);
        shipment.setOrder(order);
        shipment.setShipmentStatus(status);
        shipment.setRecipientName("Nguyen Van A");
        shipment.setPhoneNumber("0901234567");
        order.setShipment(shipment);
        return shipment;
    }

    static Product product(long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setActive(true);
        p.setStockQuantity(0);
        return p;
    }

    static OrderItem item(long id, Order order, Product product, int quantity, String lineTotal) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(lineTotal));
        item.setLineTotal(new BigDecimal(lineTotal));
        order.getItems().add(item);
        return item;
    }

    static void signIn(String name, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(auth(name, authorities));
    }

    static void signOut() {
        SecurityContextHolder.clearContext();
    }

    static Authentication auth(String name, String... authorities) {
        var list = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(name, "n/a", list);
    }
}
