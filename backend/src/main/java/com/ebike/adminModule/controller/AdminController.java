package com.ebike.adminModule.controller;

import com.ebike.adminModule.dto.request.AdminAccountCreateRequest;
import com.ebike.adminModule.dto.request.AdminAccountRoleUpdateRequest;
import com.ebike.adminModule.dto.request.AdminAccountStatusUpdateRequest;
import com.ebike.adminModule.dto.request.PricingRuleUpdateRequest;
import com.ebike.adminModule.dto.request.PromotionCreateRequest;
import com.ebike.adminModule.dto.request.PromotionUpdateRequest;
import com.ebike.adminModule.dto.response.AdminAccountResponse;
import com.ebike.adminModule.dto.response.AdminAuditLogResponse;
import com.ebike.adminModule.dto.response.AdminOverviewResponse;
import com.ebike.adminModule.dto.response.PricingRuleResponse;
import com.ebike.adminModule.dto.response.PromotionResponse;
import com.ebike.adminModule.dto.response.RolePermissionResponse;
import com.ebike.adminModule.service.AdminService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public AdminOverviewResponse getOverview() {
        return adminService.getOverview();
    }

    @GetMapping("/pricing-rules")
    public List<PricingRuleResponse> getPricingRules() {
        return adminService.getPricingRules();
    }

    @PatchMapping("/pricing-rules/{id}")
    public PricingRuleResponse updatePricingRule(
        @PathVariable Long id,
        @RequestBody PricingRuleUpdateRequest request
    ) {
        return adminService.updatePricingRule(id, request);
    }

    @GetMapping("/promotions")
    public List<PromotionResponse> getPromotions(@RequestParam(required = false) String status) {
        return adminService.getPromotions(status);
    }

    @PostMapping("/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponse createPromotion(@RequestBody PromotionCreateRequest request) {
        return adminService.createPromotion(request);
    }

    @PatchMapping("/promotions/{id}")
    public PromotionResponse updatePromotion(
        @PathVariable Long id,
        @RequestBody PromotionUpdateRequest request
    ) {
        return adminService.updatePromotion(id, request);
    }

    @GetMapping("/accounts")
    public List<AdminAccountResponse> getAccounts(@RequestParam(required = false) String type) {
        return adminService.getAccounts(type);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAccountResponse createAccount(@RequestBody AdminAccountCreateRequest request) {
        return adminService.createAccount(request);
    }

    @PatchMapping("/accounts/{id}/role")
    public AdminAccountResponse updateAccountRole(
        @PathVariable Long id,
        @RequestBody AdminAccountRoleUpdateRequest request
    ) {
        return adminService.updateAccountRole(id, request);
    }

    @PatchMapping("/accounts/{id}/status")
    public AdminAccountResponse updateAccountStatus(
        @PathVariable Long id,
        @RequestBody AdminAccountStatusUpdateRequest request
    ) {
        return adminService.updateAccountStatus(id, request);
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable Long id) {
        adminService.deleteAccount(id);
    }

    @GetMapping("/roles")
    public List<RolePermissionResponse> getRoles() {
        return adminService.getRoles();
    }

    @GetMapping("/audit-logs")
    public List<AdminAuditLogResponse> getAuditLogs() {
        return adminService.getAuditLogs();
    }
}
