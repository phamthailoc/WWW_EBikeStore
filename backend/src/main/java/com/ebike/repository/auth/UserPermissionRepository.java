package com.ebike.repository.auth;

import com.ebike.model.entity.auth.UserPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    List<UserPermission> findByUserId(Long userId);
}
