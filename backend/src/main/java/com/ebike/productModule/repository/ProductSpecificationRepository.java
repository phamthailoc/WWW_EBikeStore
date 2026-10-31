package com.ebike.productModule.repository;

import com.ebike.productModule.entity.ProductSpecification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {

    Optional<ProductSpecification> findByProductId(Long productId);
}
