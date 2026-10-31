package com.ebike.orderModule.service;

import com.ebike.orderModule.dto.request.VnPayCreatePaymentRequest;
import com.ebike.orderModule.dto.response.VnPayCreatePaymentResponse;
import com.ebike.orderModule.dto.response.VnPayReturnResponse;
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
