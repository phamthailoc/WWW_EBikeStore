package com.ebike.repository.admin;

import com.ebike.model.entity.admin.Promotion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    boolean existsByCode(String code);

    Optional<Promotion> findByCode(String code);

    Optional<Promotion> findByCodeIgnoreCase(String code);

    @Modifying
    @Query("update Promotion p set p.usageCount = p.usageCount + 1 "
        + "where p.id = :id and (p.usageLimit = 0 or p.usageCount < p.usageLimit)")
    int tryIncrementUsage(@Param("id") Long id);
}
