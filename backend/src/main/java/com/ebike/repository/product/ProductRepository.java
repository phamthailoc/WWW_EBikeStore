package com.ebike.repository.product;

import com.ebike.model.entity.product.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    /** V26: every bike with its colours, for the pick list of a stock receipt (withdrawn bikes too: stock can still arrive for them). */
    @Query("select distinct p from Product p left join fetch p.variants order by p.name asc")
    java.util.List<Product> findAllWithVariants();

    @EntityGraph(attributePaths = {
        "category",
        "specification",
        "variants",
        "variants.images",
        "images"
    })
    @Query("select p from Product p where p.slug = :slug")
    Optional<Product> findDetailBySlug(@Param("slug") String slug);
}
