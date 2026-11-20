package com.ebike.repository.order;

import com.ebike.model.entity.order.OrderItemUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemUnitRepository extends JpaRepository<OrderItemUnit, Long> {

    List<OrderItemUnit> findByFrameNumberIn(Collection<String> frameNumbers);

    /** V20: everything the warranty book needs to know about a vehicle, in one query. */
    String WARRANTY_FETCH = "select u from OrderItemUnit u join fetch u.orderItem i join fetch i.order o "
        + "join fetch i.product p left join fetch p.specification sp left join fetch i.productVariant v "
        + "left join fetch o.shipment s left join fetch o.user usr ";

    @Query(WARRANTY_FETCH + "where usr.username = :username and o.status = com.ebike.model.entity.order.OrderStatus.DELIVERED order by u.id desc")
    List<OrderItemUnit> findDeliveredByUsername(@Param("username") String username);

    /** Row lock taken before a warranty request is checked, so two requests for one bike cannot both pass the "no open request" check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderItemUnit> findLockedById(Long id);

    @Query(WARRANTY_FETCH + "where u.id = :id")
    Optional<OrderItemUnit> findForWarrantyById(@Param("id") Long id);

    @Query(WARRANTY_FETCH + "where u.frameNumber = :frame")
    Optional<OrderItemUnit> findForWarrantyByFrameNumber(@Param("frame") String frame);

    @Query(WARRANTY_FETCH + "where o.status = com.ebike.model.entity.order.OrderStatus.DELIVERED and usr is not null")
    List<OrderItemUnit> findAllDeliveredForWarranty();
}
