package com.ebike.repository.product;

import com.ebike.model.entity.product.ProductVariant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @EntityGraph(attributePaths = {"product"})
    Optional<ProductVariant> findWithProductById(Long id);
}
