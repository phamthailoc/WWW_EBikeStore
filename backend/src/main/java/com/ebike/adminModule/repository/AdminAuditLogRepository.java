package com.ebike.adminModule.repository;

import com.ebike.adminModule.entity.AdminAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findTop100ByOrderByCreatedAtDesc();
}
