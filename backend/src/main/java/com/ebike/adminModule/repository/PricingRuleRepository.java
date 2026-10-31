package com.ebike.adminModule.repository;

import com.ebike.adminModule.entity.PricingRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    Optional<PricingRule> findByCode(String code);
}
