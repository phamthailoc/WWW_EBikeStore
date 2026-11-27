package com.ebike.controller.order;

import com.ebike.dto.order.request.VnPayCreatePaymentRequest;
import com.ebike.dto.order.response.VnPayCreatePaymentResponse;
import com.ebike.dto.order.response.VnPayReturnResponse;
import com.ebike.service.order.VnPayService;
import com.ebike.common.constants.PermissionConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/vnpay")
public class VnPayController {

    private final VnPayService vnPayService;

    public VnPayController(VnPayService vnPayService) {
        this.vnPayService = vnPayService;
    }

    @PostMapping("/create")
    public VnPayCreatePaymentResponse createPaymentUrl(
        @RequestBody VnPayCreatePaymentRequest request,
        HttpServletRequest servletRequest,
        Authentication authentication
    ) {
        return vnPayService.createPaymentUrl(
            request,
            resolveClientIp(servletRequest),
            authentication == null ? null : authentication.getName(),
            isBackOffice(authentication)
        );
    }

    @RequestMapping(value = "/ipn", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, String> handleIpnCallback(@RequestParam Map<String, String> params) {
        return vnPayService.handleIpnCallback(params);
    }

    @GetMapping("/return")
    public VnPayReturnResponse handleReturnUrl(@RequestParam Map<String, String> params) {
        return vnPayService.handleReturnUrl(params);
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Never read X-Forwarded-For / X-Real-IP: any client can forge them. Behind a trusted proxy, Tomcat
        // (server.forward-headers-strategy=native in the prod profile) already puts the real client address here.
        return request.getRemoteAddr();
    }

    private boolean isBackOffice(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> PermissionConstants.OrderManagement.PAYMENT_VERIFY.equals(authority.getAuthority()));
    }
}
