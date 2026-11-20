package com.ebike.repository.auth;

import com.ebike.model.entity.auth.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @EntityGraph(attributePaths = {"roles"})
    List<User> findAll();

    /** Of these accounts, the ones that hold the CUSTOMER role. */
    @org.springframework.data.jpa.repository.Query("select distinct u.id from User u join u.roles r where u.id in :ids and r.name = 'CUSTOMER'")
    List<Long> findCustomerIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

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
