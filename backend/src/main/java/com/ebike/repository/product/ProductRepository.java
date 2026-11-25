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

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** The admin catalogue: name or slug contains the (lower-case) text and the category matches; null means no filter. */
    @Query("select p from Product p join fetch p.category c where (:q is null or lower(p.name) like :q or lower(p.slug) like :q) and (:categoryId is null or c.id = :categoryId) order by p.id desc")
    java.util.List<Product> searchCatalog(@Param("q") String q, @Param("categoryId") Integer categoryId);

    @Query("select count(i) from OrderItem i where i.product.id = :productId")
    long countOrderItems(@Param("productId") Long productId);

    @Query("select i.product.id, count(i) from OrderItem i group by i.product.id")
    java.util.List<Object[]> orderItemCounts();

    @Query("select b.product.id, count(b) from TestRideBooking b group by b.product.id")
    java.util.List<Object[]> testRideCounts();

    @Query("select count(b) from TestRideBooking b where b.product.id = :productId")
    long countTestRides(@Param("productId") Long productId);

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
