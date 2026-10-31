package com.ebike.orderModule.service;

import com.ebike.orderModule.dto.response.ShowroomResponse;
import java.util.List;

public interface ShowroomService {

    List<ShowroomResponse> getShowrooms(String district);
}
