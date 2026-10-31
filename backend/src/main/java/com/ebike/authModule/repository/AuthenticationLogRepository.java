package com.ebike.authModule.repository;

import com.ebike.authModule.entity.AuthenticationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationLogRepository extends JpaRepository<AuthenticationLog, Long> {
}
