package com.ebike.orderModule.service.impl;

import com.ebike.orderModule.dto.response.ShowroomResponse;
import com.ebike.orderModule.entity.Showroom;
import com.ebike.orderModule.repository.ShowroomRepository;
import com.ebike.orderModule.service.ShowroomService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShowroomServiceImpl implements ShowroomService {

    private final ShowroomRepository showroomRepository;

    public ShowroomServiceImpl(ShowroomRepository showroomRepository) {
        this.showroomRepository = showroomRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowroomResponse> getShowrooms(String district) {
        List<Showroom> showrooms = district == null || district.isBlank()
            ? showroomRepository.findByActiveTrueOrderByDistrictAscNameAsc()
            : showroomRepository.findByActiveTrueAndDistrictIgnoreCaseOrderByNameAsc(district.trim());

        return showrooms.stream()
            .map(this::toResponse)
            .toList();
    }

    private ShowroomResponse toResponse(Showroom showroom) {
        return new ShowroomResponse(
            showroom.getId(),
            showroom.getName(),
            showroom.getCity(),
            showroom.getDistrict(),
            showroom.getAddress(),
            showroom.getPhone(),
            showroom.getOpeningHours(),
            showroom.getActive()
        );
    }
}
