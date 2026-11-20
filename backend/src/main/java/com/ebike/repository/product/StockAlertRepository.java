package com.ebike.repository.product;

import com.ebike.model.entity.product.StockAlert;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<StockAlert> findByUserUsernameOrderByCreatedAtDesc(String username);

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    Optional<StockAlert> findByUserIdAndVariantId(Long userId, Long variantId);

    @EntityGraph(attributePaths = {"user"})
    List<StockAlert> findByVariantIdAndNotifiedAtIsNull(Long variantId);

    void deleteByUserId(Long userId);

    /**
     * Marks one waiting row as used. Returns 1 for the caller that won and 0 when another request or the periodic sweep
     * already told this customer, so nobody gets two notifications for one restock.
     */
    @Modifying
    @Query("update StockAlert a set a.notifiedAt = :now where a.id = :id and a.notifiedAt is null")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Colours someone is still waiting for that can be bought right now (on sale and in stock). */
    @Query("select distinct a.variant.id from StockAlert a where a.notifiedAt is null and a.variant.stockQuantity > 0 "
        + "and a.variant.active = true and a.variant.product.active = true")
    List<Long> findWaitingVariantIdsInStock();
}
