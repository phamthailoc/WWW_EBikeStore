package com.ebike.repository.order;

import com.ebike.model.entity.order.OrderEmailVerificationSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEmailVerificationSessionRepository extends JpaRepository<OrderEmailVerificationSession, UUID> {

    Optional<OrderEmailVerificationSession> findFirstByEmailOrderByCreatedAtDesc(String email);
}
