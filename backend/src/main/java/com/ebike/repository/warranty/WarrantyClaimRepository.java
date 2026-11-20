package com.ebike.repository.warranty;

import com.ebike.model.entity.warranty.WarrantyClaim;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, Long> {

    // The graph holds everything WarrantyService.toClaimResponse reads, so a list is one query instead of several per row.


    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "unit.orderItem.order", "unit.orderItem.order.shipment",
        "unit.orderItem.product", "unit.orderItem.product.specification", "unit.orderItem.productVariant", "user"})
    List<WarrantyClaim> findByUserUsernameOrderByCreatedAtDesc(String username);

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "unit.orderItem.order", "unit.orderItem.order.shipment",
        "unit.orderItem.product", "unit.orderItem.product.specification", "unit.orderItem.productVariant", "user"})
    List<WarrantyClaim> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "unit.orderItem.order", "unit.orderItem.order.shipment",
        "unit.orderItem.product", "unit.orderItem.product.specification", "unit.orderItem.productVariant", "user"})
    List<WarrantyClaim> findByStatusOrderByCreatedAtDesc(String status);

    boolean existsByUnitIdAndStatusIn(Long unitId, Collection<String> statuses);

    List<WarrantyClaim> findByUnitIdIn(Collection<Long> unitIds);

    /** Account erasure: the store keeps its repair history, the customer's own words go. */
    @Modifying
    @Query("update WarrantyClaim c set c.description = :text where c.user.id = :userId")
    int clearDescriptionsOf(@Param("userId") Long userId, @Param("text") String text);
}
