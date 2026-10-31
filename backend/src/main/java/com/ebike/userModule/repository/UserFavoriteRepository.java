package com.ebike.userModule.repository;

import com.ebike.userModule.entity.UserFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    List<UserFavorite> findByUserUsernameAndProductActiveTrueOrderByCreatedAtDesc(String username);

    Optional<UserFavorite> findByUserUsernameAndProductId(String username, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
