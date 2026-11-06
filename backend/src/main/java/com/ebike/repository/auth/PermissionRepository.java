package com.ebike.repository.auth;

import com.ebike.model.entity.auth.Permission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {

    Optional<Permission> findByCode(String code);
}
