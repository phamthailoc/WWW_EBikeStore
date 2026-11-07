package com.ebike.repository.support;

import com.ebike.model.entity.support.SupportRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    List<SupportRequest> findAllByOrderByCreatedAtDesc();

    List<SupportRequest> findByStatusOrderByCreatedAtDesc(String status);

    /** Used to slow down someone who floods the form: how many requests this email sent recently. */
    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, LocalDateTime after);
}
