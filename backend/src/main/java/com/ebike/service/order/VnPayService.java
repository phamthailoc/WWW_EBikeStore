package com.ebike.service.order;

import com.ebike.dto.order.request.VnPayCreatePaymentRequest;
import com.ebike.dto.order.response.VnPayCreatePaymentResponse;
import com.ebike.dto.order.response.VnPayReturnResponse;
import java.util.Map;

public interface VnPayService {

    VnPayCreatePaymentResponse createPaymentUrl(
        VnPayCreatePaymentRequest request,
        String clientIp,
        String username,
        boolean backOfficeUser
    );

    Map<String, String> handleIpnCallback(Map<String, String> params);

    VnPayReturnResponse handleReturnUrl(Map<String, String> params);
}
