package com.ebike.repository.auth;

import com.ebike.model.entity.auth.AuthenticationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationLogRepository extends JpaRepository<AuthenticationLog, Long> {

    /** Sign-in history belongs to the account: it goes with it when an account without orders is deleted. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from AuthenticationLog l where l.user.id = :userId")
    void deleteByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
