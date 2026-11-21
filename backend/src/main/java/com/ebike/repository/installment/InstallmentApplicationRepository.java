package com.ebike.repository.installment;

import com.ebike.model.entity.installment.InstallmentApplication;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstallmentApplicationRepository extends JpaRepository<InstallmentApplication, Long> {

    /** Row lock for whoever decides an application: two staff pressing "approve" and "reject" at once wait for each other instead of both passing the status check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from InstallmentApplication a where a.id = :id")
    Optional<InstallmentApplication> findLockedById(@Param("id") Long id);

    /** The order of an application, read without loading the application, so the order row can be locked before the application row. */
    @Query("select a.order.id from InstallmentApplication a where a.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    Optional<InstallmentApplication> findByOrderId(Long orderId);

    /** The applications of several orders at once, so a list of orders does not ask once per row. */
    List<InstallmentApplication> findByOrderIdIn(Collection<Long> orderIds);

    /** Applications that still hold an order's stock: waiting for the staff, or approved with the down payment not paid yet (the order is still PENDING). */
    @Query("select count(a) from InstallmentApplication a where a.user.id = :userId and a.status in ('PENDING', 'APPROVED') "
        + "and a.order.status = com.ebike.model.entity.order.OrderStatus.PENDING")
    long countOpenOf(@Param("userId") Long userId);

    long countByStatus(String status);

    /** Approved applications whose down payment has not come in: the order stays PENDING until it does (paying confirms it). */
    @Query("select count(a) from InstallmentApplication a where a.status = 'APPROVED' and a.order.status = com.ebike.model.entity.order.OrderStatus.PENDING")
    long countAwaitingDownPayment();

    /**
     * The staff queue: oldest first, so the application that has waited longest is on top. The graph brings what a row shows in the same query; the
     * vehicles of the orders (a collection, which a paged query cannot join) load in batches, see hibernate.default_batch_fetch_size.
     */
    @EntityGraph(attributePaths = {"user", "order", "order.payment", "order.shipment", "reviewedBy"})
    List<InstallmentApplication> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "order", "order.payment", "order.shipment", "reviewedBy"})
    List<InstallmentApplication> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "order", "order.payment", "order.shipment", "reviewedBy"})
    List<InstallmentApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** The customer's own applications, newest first. */
    @EntityGraph(attributePaths = {"user", "order"})
    List<InstallmentApplication> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** The order was cancelled: an application that was still open is closed with it (a rejected one keeps its verdict). */
    @Modifying
    @Query("update InstallmentApplication a set a.status = 'CANCELLED', a.updatedAt = :now where a.order.id = :orderId and a.status in ('PENDING', 'APPROVED')")
    int cancelOpenOfOrder(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    /** V6 account erasure: the ID number and the income are personal data; the rest of the row is the shop's record of the plan and stays. */
    @Modifying
    @Query("update InstallmentApplication a set a.idNumberMasked = null, a.monthlyIncome = null, a.updatedAt = :now where a.user.id = :userId")
    int clearPersonalDataOf(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
