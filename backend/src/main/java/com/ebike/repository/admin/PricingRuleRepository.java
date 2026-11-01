package com.ebike.repository.admin;

import com.ebike.model.entity.admin.PricingRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    Optional<PricingRule> findByCode(String code);
}
