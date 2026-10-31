package com.ebike.orderModule.repository;

import com.ebike.orderModule.entity.Payment;
import com.ebike.orderModule.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndPaymentStatus(Long orderId, PaymentStatus paymentStatus);

    Optional<Payment> findByTransactionReference(String transactionReference);

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
