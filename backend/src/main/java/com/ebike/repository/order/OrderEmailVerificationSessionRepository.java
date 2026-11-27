package com.ebike.repository.order;

import com.ebike.model.entity.order.OrderEmailVerificationSession;
import jakarta.persistence.LockModeType;
import com.ebike.model.entity.order.OrderEmailVerificationStatus;
import java.time.LocalDateTime;
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

    Optional<OrderEmailVerificationSession> findFirstByEmailAndRequesterKeyOrderByCreatedAtDesc(String email, String requesterKey);

    /** Sends in the window; served by idx_order_email_verification_email_created (email, created_at). */
    long countByEmailAndCreatedAtAfter(String email, LocalDateTime after);

    /** Sessions locked by too many wrong codes in the window. */
    long countByEmailAndStatusAndAttemptCountGreaterThanEqualAndCreatedAtAfter(
        String email, OrderEmailVerificationStatus status, int attempts, LocalDateTime after);
}
