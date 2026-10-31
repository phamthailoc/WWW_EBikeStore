package com.ebike.adminModule.repository;

import com.ebike.adminModule.entity.Promotion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    boolean existsByCode(String code);

    Optional<Promotion> findByCode(String code);
}
