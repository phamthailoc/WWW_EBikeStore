package com.ebike.repository.order;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByUserId(Long userId);

    List<Order> findByUserIsNullAndCustomerEmail(String customerEmail);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    /** True when the customer already has an order, other than one in the given status, that used the code. */
    boolean existsByUserIdAndPromotionIdAndStatusNot(Long userId, Long promotionId, OrderStatus status);

    boolean existsByUserIsNullAndCustomerEmailIgnoreCaseAndPromotionIdAndStatusNot(String email, Long promotionId, OrderStatus status);

    List<Order> findByIdIn(Collection<Long> ids);
}
