package com.ebike.service.order;

import com.ebike.dto.order.response.ShowroomResponse;
import java.util.List;

public interface ShowroomService {

    List<ShowroomResponse> getShowrooms(String district);
}
