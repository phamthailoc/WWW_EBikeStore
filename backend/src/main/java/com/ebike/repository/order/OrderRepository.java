package com.ebike.repository.order;

import com.ebike.model.entity.order.Order;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByUserId(Long userId);

    List<Order> findByUserIsNullAndCustomerEmail(String customerEmail);

    List<Order> findByIdIn(Collection<Long> ids);
}
