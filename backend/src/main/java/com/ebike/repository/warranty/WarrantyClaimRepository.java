package com.ebike.repository.warranty;

import com.ebike.model.entity.warranty.WarrantyClaim;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<WarrantyClaim> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "unit.orderItem.order", "unit.orderItem.order.shipment", "unit.orderItem.order.payment",
        "unit.orderItem.product", "unit.orderItem.product.specification", "unit.orderItem.productVariant", "user", "user.preference"})
    List<WarrantyClaim> findByStatus(String status, Pageable pageable);

    boolean existsByUnitIdAndStatusIn(Long unitId, Collection<String> statuses);

    List<WarrantyClaim> findByUnitIdIn(Collection<Long> unitIds);

    /** Account erasure: the store keeps its repair history, the customer's own words go. */
    @Modifying
    @Query("update WarrantyClaim c set c.description = :text where c.user.id = :userId")
    int clearDescriptionsOf(@Param("userId") Long userId, @Param("text") String text);
}
