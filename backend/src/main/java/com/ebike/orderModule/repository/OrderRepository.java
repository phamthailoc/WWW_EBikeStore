package com.ebike.orderModule.repository;

import com.ebike.orderModule.entity.Order;
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
