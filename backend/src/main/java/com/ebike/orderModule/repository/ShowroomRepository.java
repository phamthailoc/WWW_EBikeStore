package com.ebike.orderModule.repository;

import com.ebike.orderModule.entity.Showroom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowroomRepository extends JpaRepository<Showroom, Long> {

    List<Showroom> findByActiveTrueOrderByDistrictAscNameAsc();

    List<Showroom> findByActiveTrueAndDistrictIgnoreCaseOrderByNameAsc(String district);
}
