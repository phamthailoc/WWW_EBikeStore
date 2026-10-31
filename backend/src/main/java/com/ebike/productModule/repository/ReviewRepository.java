package com.ebike.productModule.repository;

import com.ebike.productModule.entity.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductSlugOrderByCreatedAtDesc(String slug);

    List<Review> findByProductId(Long productId);

    Optional<Review> findByProductSlugAndUserUsername(String slug, String username);
}
