package com.ebike.repository.admin;

import com.ebike.model.entity.admin.Promotion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    boolean existsByCode(String code);

    Optional<Promotion> findByCode(String code);
}
