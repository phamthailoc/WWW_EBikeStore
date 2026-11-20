package com.ebike.service.admin;

import com.ebike.dto.admin.request.AdminAccountCreateRequest;
import com.ebike.dto.admin.request.AdminAccountRoleUpdateRequest;
import com.ebike.dto.admin.request.AdminAccountStatusUpdateRequest;
import com.ebike.dto.admin.request.PricingRuleUpdateRequest;
import com.ebike.dto.admin.request.PromotionCreateRequest;
import com.ebike.dto.admin.request.PromotionUpdateRequest;
import com.ebike.dto.admin.response.AdminAccountResponse;
import com.ebike.dto.admin.response.AdminAuditLogResponse;
import com.ebike.dto.admin.response.AdminOverviewResponse;
import com.ebike.dto.admin.response.PricingRuleResponse;
import com.ebike.dto.admin.response.PromotionResponse;
import com.ebike.dto.admin.response.RolePermissionResponse;
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

    void deleteAccount(Long id, String confirmEmail);

    List<RolePermissionResponse> getRoles();

    List<AdminAuditLogResponse> getAuditLogs();
}
