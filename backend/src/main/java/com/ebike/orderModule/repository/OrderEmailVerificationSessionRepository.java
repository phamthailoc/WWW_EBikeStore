package com.ebike.orderModule.repository;

import com.ebike.orderModule.entity.OrderEmailVerificationSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEmailVerificationSessionRepository extends JpaRepository<OrderEmailVerificationSession, UUID> {
}
