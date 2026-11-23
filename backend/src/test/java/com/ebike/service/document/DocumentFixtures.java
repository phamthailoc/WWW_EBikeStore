package com.ebike.service.document;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.ShipmentStatus;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.ProductVariant;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds the orders the document tests print: no database, only the plain entities. */
final class DocumentFixtures {

    static final LocalDateTime ORDERED = LocalDateTime.of(2026, 11, 10, 9, 30);
    static final LocalDateTime HANDED_OVER = LocalDateTime.of(2026, 11, 20, 15, 45);

    private DocumentFixtures() {
    }

    static StoreProfile store() {
        return new StoreProfile("Kinetic E-Bike", "Công ty TNHH Kinetic E-Bike (mô phỏng)", "0123456789",
            "12 Nguyễn Văn Linh, Quận 7, TP. Hồ Chí Minh", "1900 0000", "support@kinetic-ebike.local", "https://shop.example.com/");
    }

    static User user(long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFirstName("Nguyễn");
        user.setLastName("Văn An");
        return user;
    }

    static Product product(long id, String name, Integer warrantyMonths) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSlug("xe-" + id);
        ProductSpecification specification = new ProductSpecification();
        specification.setWarrantyMonths(warrantyMonths);
        product.setSpecification(specification);
        return product;
    }

    /** A pay-later order of {@code quantity} bikes at 20.000.000 ₫ each, with a pickup showroom, in the given status; the units are numbered when asked. */
    static Order order(long id, OrderStatus status, int quantity, boolean numbered) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber("ORD-20261110-" + String.format("%08d", id));
        order.setStatus(status);
        order.setUser(user(7, "an"));
        order.setCustomerEmail("an@example.com");
        order.setNotes("Gọi trước khi giao <b>nhé</b> & cảm ơn");
        ReflectionTestUtils.setField(order, "createdAt", ORDERED);
        ReflectionTestUtils.setField(order, "updatedAt", HANDED_OVER);
        if (status == OrderStatus.DELIVERED) {
            order.setDeliveredAt(HANDED_OVER);
        }

        Product product = product(3, "Kinetic Aero X", 24);
        ProductVariant variant = new ProductVariant();
        variant.setId(11L);
        variant.setColorName("Xanh rêu");
        OrderItem item = new OrderItem();
        item.setId(100L);
        item.setOrder(order);
        item.setProduct(product);
        item.setProductVariant(variant);
        item.setProductName("Kinetic Aero X");
        item.setUnitPrice(new BigDecimal("20000000"));
        item.setQuantity(quantity);
        item.setLineTotal(new BigDecimal("20000000").multiply(BigDecimal.valueOf(quantity)));
        if (numbered) {
            for (int index = 1; index <= quantity; index++) {
                OrderItemUnit unit = new OrderItemUnit();
                ReflectionTestUtils.setField(unit, "id", 500L + index);
                unit.setOrderItem(item);
                unit.setFrameNumber("RLEBK" + String.format("%05d", index));
                unit.setEngineNumber("EN" + String.format("%05d", index));
                unit.setWarrantyMonths(24);
                item.getUnits().add(unit);
            }
        }
        order.getItems().add(item);
        order.setSubtotal(item.getLineTotal());
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setRegistrationFee(BigDecimal.ZERO);
        order.setIncludeRegistrationService(false);
        order.setTotalAmount(item.getLineTotal());

        Showroom showroom = new Showroom();
        showroom.setId(1L);
        showroom.setName("Showroom Quận 7");
        showroom.setAddress("12 Nguyễn Văn Linh, Quận 7");
        Shipment shipment = new Shipment();
        shipment.setOrder(order);
        shipment.setShipmentStatus(status == OrderStatus.DELIVERED ? ShipmentStatus.DELIVERED : ShipmentStatus.PENDING);
        shipment.setRecipientName("Nguyễn Văn An");
        shipment.setPhoneNumber("0901234567");
        shipment.setPickupShowroom(showroom);
        if (status == OrderStatus.DELIVERED) {
            shipment.setDeliveredAt(HANDED_OVER);
        }
        order.setShipment(shipment);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.PAY_LATER);
        payment.setPaymentStatus(status == OrderStatus.DELIVERED ? PaymentStatus.PAID : PaymentStatus.PENDING);
        payment.setAmount(order.getTotalAmount());
        if (status == OrderStatus.DELIVERED) {
            payment.setPaidAt(HANDED_OVER);
        }
        order.setPayment(payment);
        return order;
    }
}
