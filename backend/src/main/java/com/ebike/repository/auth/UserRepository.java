package com.ebike.repository.auth;

import com.ebike.model.entity.auth.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @EntityGraph(attributePaths = {"roles"})
    List<User> findAll();

    /** Of these accounts, the ones that hold the CUSTOMER role; the name is trimmed and upper-cased like the JWT filter does when it builds ROLE_*. */
    @Query("select distinct u.id from User u join u.roles r where u.id in :ids and upper(trim(r.name)) = 'CUSTOMER'")
    List<Long> findCustomerIds(@Param("ids") Collection<Long> ids);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    /** V23: row lock on the account, so one customer's parallel test-ride bookings are counted one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findLockedByUsername(@Param("username") String username);

    @EntityGraph(attributePaths = {
        "roles",
        "roles.permissions",
        "userPermissions",
        "userPermissions.permission"
    })
    Optional<User> findWithPermissionsByUsername(String username);

    @EntityGraph(attributePaths = {
        "roles",
        "roles.permissions",
        "userPermissions",
        "userPermissions.permission"
    })
    Optional<User> findWithPermissionsByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}
