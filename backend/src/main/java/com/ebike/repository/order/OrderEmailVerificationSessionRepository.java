package com.ebike.repository.order;

import com.ebike.model.entity.order.OrderEmailVerificationSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderEmailVerificationSessionRepository extends JpaRepository<OrderEmailVerificationSession, UUID> {

    /** Row lock for checking a code or spending a proof: parallel guesses on one session wait for each other, so every wrong code is counted and a proof is consumed once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderEmailVerificationSession s where s.id = :id")
    Optional<OrderEmailVerificationSession> findLockedById(@Param("id") UUID id);

    Optional<OrderEmailVerificationSession> findFirstByEmailOrderByCreatedAtDesc(String email);
}
