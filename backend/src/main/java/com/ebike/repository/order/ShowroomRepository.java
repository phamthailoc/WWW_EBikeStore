package com.ebike.repository.order;

import com.ebike.model.entity.order.Showroom;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowroomRepository extends JpaRepository<Showroom, Long> {

    List<Showroom> findByActiveTrueOrderByDistrictAscNameAsc();

    List<Showroom> findByActiveTrueAndDistrictIgnoreCaseOrderByNameAsc(String district);

    /** V23: row lock held while a test ride is booked, so two customers cannot both take the last place of a slot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Showroom s where s.id = :id")
    Optional<Showroom> findLockedById(@Param("id") Long id);
}
