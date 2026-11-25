package com.ebike.repository.product;

import com.ebike.model.entity.product.ProductSpecification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {

    Optional<ProductSpecification> findByProductId(Long productId);
}
