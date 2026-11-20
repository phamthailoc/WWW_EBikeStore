package com.ebike.repository.product;

import com.ebike.model.entity.product.ProductVariant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findWithProductById(Long id);

    /**
     * Scalar read straight from the database: unlike {@link #findWithProductById} it never hands back an entity that
     * is already cached in the current persistence context (open-in-view), so it sees a restock committed meanwhile.
     */
    /** The stock of a colour that is on sale (colour and bike both active); empty when it is withdrawn or does not exist. */
    @org.springframework.data.jpa.repository.Query("select coalesce(v.stockQuantity, 0) from ProductVariant v where v.id = :id and v.active = true "
        + "and v.product.active = true")
    Optional<Integer> findStockOnSale(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("select count(v) > 0 from ProductVariant v where v.id = :id and v.active = true "
        + "and v.product.active = true and v.stockQuantity > 0")
    boolean isOnSaleAndInStock(@org.springframework.data.repository.query.Param("id") Long id);
}
