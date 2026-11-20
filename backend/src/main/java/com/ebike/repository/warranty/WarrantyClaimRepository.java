package com.ebike.repository.warranty;

import com.ebike.model.entity.warranty.WarrantyClaim;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, Long> {

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "user"})
    List<WarrantyClaim> findByUserUsernameOrderByCreatedAtDesc(String username);

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "user"})
    List<WarrantyClaim> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"unit", "unit.orderItem", "user"})
    List<WarrantyClaim> findByStatusOrderByCreatedAtDesc(String status);

    boolean existsByUnitIdAndStatusIn(Long unitId, Collection<String> statuses);

    List<WarrantyClaim> findByUnitIdIn(Collection<Long> unitIds);

    void deleteByUserId(Long userId);
}
