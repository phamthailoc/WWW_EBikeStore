package com.ebike.repository.order;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByUserId(Long userId);

    /** True when the customer has had the product delivered, which is what entitles them to review it. */
    @Query("select count(o) > 0 from Order o join o.items i where o.user.id = :userId and i.product.id = :productId "
        + "and o.status = com.ebike.model.entity.order.OrderStatus.DELIVERED")
    boolean hasDeliveredProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("select distinct o.user.id from Order o join o.items i where o.user is not null and i.product.id = :productId "
        + "and o.status = com.ebike.model.entity.order.OrderStatus.DELIVERED")
    List<Long> findDeliveredBuyerIds(@Param("productId") Long productId);

    List<Order> findByUserId(Long userId);

    List<Order> findByUserIsNullAndCustomerEmail(String customerEmail);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

    /** True when the customer already has an order, other than one in the given status, that used the code. */
    boolean existsByUserIdAndPromotionIdAndStatusNot(Long userId, Long promotionId, OrderStatus status);

    boolean existsByUserIsNullAndCustomerEmailIgnoreCaseAndPromotionIdAndStatusNot(String email, Long promotionId, OrderStatus status);

    List<Order> findByIdIn(Collection<Long> ids);
}
