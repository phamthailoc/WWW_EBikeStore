package com.ebike.repository.user;

import com.ebike.model.entity.user.UserPreference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    Optional<UserPreference> findByUserId(Long userId);

    List<UserPreference> findByUserIdIn(Collection<Long> userIds);
}
