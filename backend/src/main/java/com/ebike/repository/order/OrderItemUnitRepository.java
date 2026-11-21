package com.ebike.repository.order;

import com.ebike.model.entity.order.OrderItemUnit;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemUnitRepository extends JpaRepository<OrderItemUnit, Long> {

    List<OrderItemUnit> findByFrameNumberIn(Collection<String> frameNumbers);

    /** V20: everything the warranty book needs to know about a vehicle, in one query. */
    String WARRANTY_FETCH = "select u from OrderItemUnit u join fetch u.orderItem i join fetch i.order o "
        + "join fetch i.product p left join fetch p.specification sp left join fetch i.productVariant v "
        + "left join fetch o.shipment s left join fetch o.payment pay left join fetch o.user usr left join fetch usr.preference pref ";

    @Query(WARRANTY_FETCH + "where usr.username = :username and o.status = com.ebike.model.entity.order.OrderStatus.DELIVERED order by u.id desc")
    List<OrderItemUnit> findDeliveredByUsername(@Param("username") String username);

    /**
     * Row lock on the one vehicle, taken before a warranty request is checked, so two requests for one bike cannot both pass the "no open request"
     * check. It names no join on purpose: MariaDB has no FOR UPDATE OF, so a join would lock the order and the customer row as well.
     * Whose vehicle it is gets checked by the caller on the next read.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from OrderItemUnit u where u.id = :id")
    Optional<OrderItemUnit> findLockedById(@Param("id") Long id);

    /** Row locks on every vehicle of an order, taken first by staff who save its numbers (see OrderUnitService.replaceUnits). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from OrderItemUnit u where u.orderItem.order.id = :orderId order by u.id")
    List<OrderItemUnit> findLockedByOrderId(@Param("orderId") Long orderId);

    @Query(WARRANTY_FETCH + "where u.id = :id")
    Optional<OrderItemUnit> findForWarrantyById(@Param("id") Long id);

    @Query(WARRANTY_FETCH + "where u.frameNumber = :frame")
    Optional<OrderItemUnit> findForWarrantyByFrameNumber(@Param("frame") String frame);

    /**
     * One page of the ids of delivered vehicles of a live account, after {@code afterId}, whose hand-over is not older than {@code since}
     * (a vehicle past its last milestone has nothing left to remind, so it is not read again every day); the reminder job walks them page by page.
     * An erased account is skipped: it gets no notification or email anyway.
     */
    @Query("select u.id from OrderItemUnit u join u.orderItem i join i.order o left join o.shipment s join o.user usr "
        + "where o.status = com.ebike.model.entity.order.OrderStatus.DELIVERED and usr.accountStatus <> 'DELETED' and u.id > :afterId "
        + "and coalesce(s.deliveredAt, o.deliveredAt, o.updatedAt) >= :since order by u.id")
    List<Long> findDeliveredUnitIdsAfter(@Param("afterId") Long afterId, @Param("since") LocalDateTime since, Pageable pageable);

    @Query(WARRANTY_FETCH + "where u.id in :ids order by u.id")
    List<OrderItemUnit> findForWarrantyByIdIn(@Param("ids") Collection<Long> ids);
}
