package com.ebike.repository.product;

import com.ebike.model.entity.product.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductSlugOrderByCreatedAtDesc(String slug);

    List<Review> findByProductId(Long productId);

    List<Review> findByUserId(Long userId);

    Optional<Review> findByProductSlugAndUserUsername(String slug, String username);
}
