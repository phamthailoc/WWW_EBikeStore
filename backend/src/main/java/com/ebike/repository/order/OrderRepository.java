package com.ebike.repository.order;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByUserIdAndStatusIn(Long userId, Collection<OrderStatus> statuses);

    /** True when the customer has had the product delivered, which is what entitles them to review it. */
    @Query("select count(o) > 0 from Order o join o.items i where o.user.id = :userId and i.product.id = :productId "
        + "and o.status = com.ebike.model.entity.order.OrderStatus.DELIVERED")
    boolean hasDeliveredProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("select distinct o.user.id from Order o join o.items i where o.user is not null and i.product.id = :productId "
        + "and o.status = com.ebike.model.entity.order.OrderStatus.DELIVERED")
    List<Long> findDeliveredBuyerIds(@Param("productId") Long productId);

    List<Order> findByUserId(Long userId);

    List<Order> findByUserIsNullAndCustomerEmail(String customerEmail);

    /**
     * The orders that may have run out of time, each row locked. Cancelling, rejecting an instalment application and expiring all take the order row first
     * (then the application row), so two of them can never both cancel the order or wait for each other in a circle.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.status = :status and o.createdAt < :before")
    List<Order> findLockedByStatusAndCreatedAtBefore(@Param("status") OrderStatus status, @Param("before") LocalDateTime before);

    /** Row lock for whoever changes the state of an order; it must be the first read of the order in the transaction, or the row is already in memory in its old state. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findLockedById(@Param("id") Long id);

    /** True when the customer already has an order, other than one in the given status, that used the code. */
    boolean existsByUserIdAndPromotionIdAndStatusNot(Long userId, Long promotionId, OrderStatus status);

    boolean existsByUserIsNullAndCustomerEmailIgnoreCaseAndPromotionIdAndStatusNot(String email, Long promotionId, OrderStatus status);
}
