package com.ebike.service.order.impl;

import com.ebike.dto.order.response.ShowroomResponse;
import com.ebike.model.entity.order.Showroom;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.service.order.ShowroomService;
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
