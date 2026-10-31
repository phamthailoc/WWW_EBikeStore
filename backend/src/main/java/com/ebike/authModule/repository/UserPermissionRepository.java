package com.ebike.authModule.repository;

import com.ebike.authModule.entity.UserPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    List<UserPermission> findByUserId(Long userId);
}
