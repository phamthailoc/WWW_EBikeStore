package com.ebike.repository.admin;

import com.ebike.model.entity.admin.PricingRule;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    Optional<PricingRule> findByCode(String code);

    /** Several rules in one query (the instalment terms are read together). */
    List<PricingRule> findByCodeIn(Collection<String> codes);
}
