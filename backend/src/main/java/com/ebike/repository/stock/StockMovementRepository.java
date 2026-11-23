package com.ebike.repository.stock;

import com.ebike.model.entity.stock.StockMovement;
import com.ebike.model.entity.stock.StockMovementReason;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** The newest lines of a bike, all colours together, older than {@code beforeId} (pass Long.MAX_VALUE for the first page). */
    @Query("select m from StockMovement m left join fetch m.variant left join fetch m.actor "
        + "where m.product.id = :productId and m.reason in :reasons and m.id < :beforeId order by m.id desc")
    List<StockMovement> findOfProduct(
        @Param("productId") Long productId,
        @Param("reasons") Collection<StockMovementReason> reasons,
        @Param("beforeId") Long beforeId,
        Pageable pageable);

    /** The newest lines of one colour. */
    @Query("select m from StockMovement m left join fetch m.variant left join fetch m.actor "
        + "where m.variant.id = :variantId and m.reason in :reasons and m.id < :beforeId order by m.id desc")
    List<StockMovement> findOfVariant(
        @Param("variantId") Long variantId,
        @Param("reasons") Collection<StockMovementReason> reasons,
        @Param("beforeId") Long beforeId,
        Pageable pageable);

    /** What all the lines of a colour add up to; equal to the colour's stock while nothing changes stock outside the ledger. */
    @Query("select coalesce(sum(m.quantityChange), 0L) from StockMovement m where m.variant.id = :variantId")
    Long sumOfVariant(@Param("variantId") Long variantId);

    /** The same for a bike that has no colours (its own stock). */
    @Query("select coalesce(sum(m.quantityChange), 0L) from StockMovement m where m.product.id = :productId and m.variant is null")
    Long sumOfProductLevel(@Param("productId") Long productId);

    /** The lines of one order (a sale and, later, the cancellation that put the bikes back). */
    List<StockMovement> findByOrderIdOrderByIdAsc(Long orderId);
}
