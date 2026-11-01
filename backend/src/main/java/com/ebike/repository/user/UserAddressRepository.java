package com.ebike.repository.user;

import com.ebike.model.entity.user.UserAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findByUserId(Long userId);

    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    List<UserAddress> findByUserIdAndDefaultAddressTrue(Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
