package com.ebike.adminModule.dto.response;

import java.util.List;

public record AdminOverviewResponse(
    List<PricingRuleResponse> pricingRules,
    List<PromotionResponse> promotions,
    List<AdminAccountResponse> accounts,
    List<AdminAuditLogResponse> auditLogs,
    long totalAccounts,
    long adminAccounts,
    long customerAccounts,
    long lockedAccounts
) {
}
