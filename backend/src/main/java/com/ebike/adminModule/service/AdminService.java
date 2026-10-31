package com.ebike.adminModule.service;

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
import java.util.List;

public interface AdminService {

    AdminOverviewResponse getOverview();

    List<PricingRuleResponse> getPricingRules();

    PricingRuleResponse updatePricingRule(Long id, PricingRuleUpdateRequest request);

    List<PromotionResponse> getPromotions(String status);

    PromotionResponse createPromotion(PromotionCreateRequest request);

    PromotionResponse updatePromotion(Long id, PromotionUpdateRequest request);

    List<AdminAccountResponse> getAccounts(String type);

    AdminAccountResponse createAccount(AdminAccountCreateRequest request);

    AdminAccountResponse updateAccountRole(Long id, AdminAccountRoleUpdateRequest request);

    AdminAccountResponse updateAccountStatus(Long id, AdminAccountStatusUpdateRequest request);

    void deleteAccount(Long id);

    List<RolePermissionResponse> getRoles();

    List<AdminAuditLogResponse> getAuditLogs();
}
