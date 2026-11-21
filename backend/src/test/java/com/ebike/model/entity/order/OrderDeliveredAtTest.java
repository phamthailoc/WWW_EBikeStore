package com.ebike.model.entity.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** V20: the hand-over time is fixed once, so the warranty does not move when the order row is saved again. */
class OrderDeliveredAtTest {

    @Test
    void anOrderThatIsNotDeliveredHasNoDeliveryTime() {
        Order order = new Order();
        order.setStatus(OrderStatus.SHIPPED);
        order.onUpdate();
        assertNull(order.getDeliveredAt());
    }

    @Test
    void theDeliveryTimeIsSetOnceAndNeverMoves() {
        Order order = new Order();
        order.setStatus(OrderStatus.DELIVERED);
        order.onUpdate();
        LocalDateTime first = order.getDeliveredAt();
        assertNotNull(first);
        order.onUpdate();
        order.onUpdate();
        assertEquals(first, order.getDeliveredAt());
    }

    @Test
    void aTimeAlreadyStoredIsKept() {
        Order order = new Order();
        LocalDateTime stored = LocalDateTime.of(2026, 1, 5, 9, 30);
        order.setDeliveredAt(stored);
        order.setStatus(OrderStatus.DELIVERED);
        order.onUpdate();
        assertEquals(stored, order.getDeliveredAt());
    }
}
