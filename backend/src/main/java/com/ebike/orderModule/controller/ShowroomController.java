package com.ebike.orderModule.controller;

import com.ebike.orderModule.dto.response.ShowroomResponse;
import com.ebike.orderModule.service.ShowroomService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/showrooms")
public class ShowroomController {

    private final ShowroomService showroomService;

    public ShowroomController(ShowroomService showroomService) {
        this.showroomService = showroomService;
    }

    @GetMapping
    public List<ShowroomResponse> getShowrooms(@RequestParam(required = false) String district) {
        return showroomService.getShowrooms(district);
    }
}
