package com.ebike.repository.product;

import com.ebike.model.entity.product.StockAlert;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<StockAlert> findByUserUsernameOrderByCreatedAtDesc(String username);

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    Optional<StockAlert> findByUserIdAndVariantId(Long userId, Long variantId);

    /**
     * The rows still waiting for a colour, locked until the transaction ends. A second restock (another manager, the
     * sweep) waits here, then sees the rows already used and finds nothing, so nobody is told twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StockAlert a where a.variant.id = :variantId and a.notifiedAt is null")
    List<StockAlert> lockWaitingByVariantId(@Param("variantId") Long variantId);

    void deleteByUserId(Long userId);

    /** Colours someone is still waiting for that can be bought right now (on sale and in stock). */
    @Query("select distinct a.variant.id from StockAlert a where a.notifiedAt is null and a.variant.stockQuantity > 0 "
        + "and a.variant.active = true and a.variant.product.active = true")
    List<Long> findWaitingVariantIdsInStock();
}
