package com.ebike.productModule.repository;

import com.ebike.productModule.entity.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

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
