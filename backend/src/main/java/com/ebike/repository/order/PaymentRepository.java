package com.ebike.repository.order;

import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByTransactionReference(String transactionReference);

    /** The order of a payment, read as a bare id, so the order row can be locked before the payment row is read (see OrderRepository.findLockedById). */
    @Query("select p.order.id from Payment p where p.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    @Query("select p.order.id from Payment p where p.transactionReference = :reference")
    Optional<Long> findOrderIdByTransactionReference(@Param("reference") String reference);

    @Query("""
        select p
        from Payment p
        join fetch p.order o
        left join fetch o.shipment s
        where o.user.id = :userId
          and (:paymentStatus is null or p.paymentStatus = :paymentStatus)
        order by o.createdAt desc
        """)
    List<Payment> findHistoryByUserId(
        @Param("userId") Long userId,
        @Param("paymentStatus") PaymentStatus paymentStatus
    );
}
