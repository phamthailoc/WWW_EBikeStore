package com.ebike.repository.auth;

import com.ebike.model.entity.auth.AuthenticationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationLogRepository extends JpaRepository<AuthenticationLog, Long> {
}
