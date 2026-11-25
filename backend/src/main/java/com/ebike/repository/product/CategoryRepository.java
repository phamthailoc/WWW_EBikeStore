package com.ebike.repository.product;

import com.ebike.model.entity.product.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    boolean existsBySlugAndIdNot(String slug, Integer id);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    boolean existsByNameIgnoreCase(String name);

    /** Categories whose name or slug contains the (lower-case) text; a null text lists every category. */
    @Query("select c from Category c where :q is null or lower(c.name) like :q or lower(c.slug) like :q order by c.name asc")
    List<Category> search(@Param("q") String q);

    @Query("select p.category.id, count(p) from Product p group by p.category.id")
    List<Object[]> productCounts();

    @Query("select count(p) from Product p where p.category.id = :categoryId")
    long countProducts(@Param("categoryId") Integer categoryId);
}
