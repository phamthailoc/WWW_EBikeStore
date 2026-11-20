package com.ebike.repository.product;

import com.ebike.model.entity.product.StockAlert;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    List<StockAlert> findByUserUsernameOrderByCreatedAtDesc(String username);

    Optional<StockAlert> findByUserIdAndVariantId(Long userId, Long variantId);

    List<StockAlert> findByVariantIdAndNotifiedAtIsNull(Long variantId);
}
