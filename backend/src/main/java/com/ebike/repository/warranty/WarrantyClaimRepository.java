package com.ebike.repository.warranty;

import com.ebike.model.entity.warranty.WarrantyClaim;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, Long> {

    // The graph holds everything WarrantyService.toClaimResponse reads, including the inverse one-to-one sides (order.payment, user.preference) that
    // Hibernate would otherwise load with one extra SELECT per row, so a list is one query instead of several per row.

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "unit.orderItem.order", "unit.orderItem.order.shipment", "unit.orderItem.order.payment",
        "unit.orderItem.product", "unit.orderItem.product.specification", "unit.orderItem.productVariant", "user", "user.preference"})
    List<WarrantyClaim> findByUserUsernameOrderByCreatedAtDesc(String username);

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "unit.orderItem.order", "unit.orderItem.order.shipment", "unit.orderItem.order.payment",
        "unit.orderItem.product", "unit.orderItem.product.specification", "unit.orderItem.productVariant", "user", "user.preference"})
    Slice<WarrantyClaim> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "unit.orderItem.order", "unit.orderItem.order.shipment", "unit.orderItem.order.payment",
        "unit.orderItem.product", "unit.orderItem.product.specification", "unit.orderItem.productVariant", "user", "user.preference"})
    Slice<WarrantyClaim> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "unit.orderItem.order", "unit.orderItem.order.shipment", "unit.orderItem.order.payment",
        "unit.orderItem.product", "unit.orderItem.product.specification", "unit.orderItem.productVariant", "user", "user.preference"})
    Slice<WarrantyClaim> findByStatusIn(Collection<String> statuses, Pageable pageable);

    /** Row lock for the staff who process a request: two managers moving the same request wait for each other instead of both passing the step check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WarrantyClaim c where c.id = :id")
    Optional<WarrantyClaim> findLockedById(@Param("id") Long id);

    boolean existsByUnitIdAndStatusIn(Long unitId, Collection<String> statuses);

    List<WarrantyClaim> findByUnitIdIn(Collection<Long> unitIds);

    boolean existsByUnitIdIn(Collection<Long> unitIds);

    /** Account erasure: the store keeps its repair history, the customer's own words go. */
    @Modifying
    @Query("update WarrantyClaim c set c.description = :text where c.user.id = :userId")
    int clearDescriptionsOf(@Param("userId") Long userId, @Param("text") String text);
}
