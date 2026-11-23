package com.ebike.repository.order;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * V27: an order with what a receipt and a warranty card print, in one query instead of about four per line.
     */
    @EntityGraph(attributePaths = {
        "user", "payment", "shipment", "shipment.pickupShowroom",
        "items", "items.units", "items.productVariant", "items.product", "items.product.specification"
    })
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findForDocuments(@Param("id") Long id);

    List<Order> findByUserIsNullAndCustomerEmail(String customerEmail);

    /** What the expiry job reads about an order before it decides whether to lock it: no entity, no lock, so orders that are not due are never held. */
    interface ExpiryCandidate {
        Long getId();

        LocalDateTime getCreatedAt();

        PaymentMethod getPaymentMethod();

        PaymentStatus getPaymentStatus();
    }

    /** The orders in the status created before the time, oldest id first, as plain values (no lock): the job locks only those it will cancel, see findLockedById. */
    @Query("select o.id as id, o.createdAt as createdAt, p.paymentMethod as paymentMethod, p.paymentStatus as paymentStatus "
        + "from Order o left join o.payment p where o.status = :status and o.createdAt < :before order by o.id")
    List<ExpiryCandidate> findExpiryCandidates(@Param("status") OrderStatus status, @Param("before") LocalDateTime before);

    /**
     * Row lock for whoever changes the state of an order. Cancelling, rejecting an instalment application, expiring and taking a payment all lock the order
     * row first (then the application or payment row), so two of them can never both cancel the order or wait for each other in a circle. It must be the
     * first read of the order in the transaction, or the row is already in memory in its old state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findLockedById(@Param("id") Long id);

    /** True when the customer already has an order, other than one in the given status, that used the code. */
    boolean existsByUserIdAndPromotionIdAndStatusNot(Long userId, Long promotionId, OrderStatus status);

    boolean existsByUserIsNullAndCustomerEmailIgnoreCaseAndPromotionIdAndStatusNot(String email, Long promotionId, OrderStatus status);
}
