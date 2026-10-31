package com.ebike.adminModule.service.impl;

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
import com.ebike.adminModule.entity.AdminAuditLog;
import com.ebike.adminModule.entity.PricingRule;
import com.ebike.adminModule.entity.Promotion;
import com.ebike.adminModule.repository.AdminAuditLogRepository;
import com.ebike.adminModule.repository.PricingRuleRepository;
import com.ebike.adminModule.repository.PromotionRepository;
import com.ebike.adminModule.service.AdminService;
import com.ebike.authModule.entity.Permission;
import com.ebike.authModule.entity.Role;
import com.ebike.authModule.entity.User;
import com.ebike.authModule.repository.RoleRepository;
import com.ebike.authModule.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminServiceImpl implements AdminService {

    private static final Set<String> PRICING_STATUSES = Set.of("ACTIVE", "SCHEDULED", "INACTIVE");
    private static final Set<String> PROMOTION_STATUSES = Set.of("LIVE", "WAITING", "EXPIRED", "DISABLED");
    private static final Set<String> DISCOUNT_TYPES = Set.of("PERCENTAGE", "FIXED_AMOUNT");
    private static final Set<String> ACCOUNT_STATUSES = Set.of("ACTIVE", "INACTIVE", "LOCKED");
    private static final Set<String> ACCOUNT_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "OPERATOR", "SUPPORT", "MAINTENANCE", "CUSTOMER");

    private final PricingRuleRepository pricingRuleRepository;
    private final PromotionRepository promotionRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminServiceImpl(
        PricingRuleRepository pricingRuleRepository,
        PromotionRepository promotionRepository,
        AdminAuditLogRepository auditLogRepository,
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.promotionRepository = promotionRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        List<AdminAccountResponse> accounts = getAccounts(null);

        return new AdminOverviewResponse(
            getPricingRules(),
            getPromotions(null),
            accounts,
            getAuditLogs(),
            accounts.size(),
            accounts.stream().filter(account -> !"CUSTOMER".equals(account.role())).count(),
            accounts.stream().filter(account -> "CUSTOMER".equals(account.role())).count(),
            accounts.stream().filter(account -> "LOCKED".equals(account.status())).count()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricingRuleResponse> getPricingRules() {
        return pricingRuleRepository.findAll().stream()
            .sorted(Comparator.comparing(PricingRule::getId))
            .map(this::toPricingRuleResponse)
            .toList();
    }

    @Override
    @Transactional
    public PricingRuleResponse updatePricingRule(Long id, PricingRuleUpdateRequest request) {
        PricingRule rule = pricingRuleRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pricing rule not found"));

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }

        rule.setAmount(request.amount());
        rule.setStatus(normalizeOrDefault(request.status(), "ACTIVE", PRICING_STATUSES));
        rule.setEffectiveFrom(request.effectiveFrom() == null ? LocalDateTime.now() : request.effectiveFrom());
        PricingRule saved = pricingRuleRepository.save(rule);
        writeAudit("cập nhật biểu phí", saved.getName());
        return toPricingRuleResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getPromotions(String status) {
        String normalizedStatus = normalizeNullable(status);

        return promotionRepository.findAll().stream()
            .filter(promotion -> normalizedStatus == null || normalizedStatus.equals(promotion.getStatus()))
            .sorted(Comparator.comparing(Promotion::getCreatedAt).reversed())
            .map(this::toPromotionResponse)
            .toList();
    }

    @Override
    @Transactional
    public PromotionResponse createPromotion(PromotionCreateRequest request) {
        String code = normalizeRequired(request.code(), "Promotion code").replaceAll("\\s+", "");
        if (promotionRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion code already exists");
        }

        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setCampaignName(normalizeRequired(request.campaignName(), "Campaign name"));
        promotion.setDiscountType(normalizeOrDefault(request.discountType(), "PERCENTAGE", DISCOUNT_TYPES));
        promotion.setDiscountValue(validatePositive(request.discountValue(), "Discount value"));
        promotion.setMaxDiscountAmount(request.maxDiscountAmount());
        promotion.setUsageLimit(request.usageLimit() == null || request.usageLimit() < 0 ? 0 : request.usageLimit());
        promotion.setUsageCount(0);
        promotion.setStatus(normalizeOrDefault(request.status(), "WAITING", PROMOTION_STATUSES));
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());

        Promotion saved = promotionRepository.save(promotion);
        writeAudit("tạo campaign", saved.getCode());
        return toPromotionResponse(saved);
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(Long id, PromotionUpdateRequest request) {
        Promotion promotion = promotionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found"));

        if (request.campaignName() != null && !request.campaignName().isBlank()) {
            promotion.setCampaignName(request.campaignName().trim());
        }
        if (request.discountType() != null && !request.discountType().isBlank()) {
            promotion.setDiscountType(normalizeOrDefault(request.discountType(), promotion.getDiscountType(), DISCOUNT_TYPES));
        }
        if (request.discountValue() != null) {
            promotion.setDiscountValue(validatePositive(request.discountValue(), "Discount value"));
        }
        if (request.maxDiscountAmount() != null) {
            promotion.setMaxDiscountAmount(request.maxDiscountAmount());
        }
        if (request.usageLimit() != null && request.usageLimit() >= 0) {
            promotion.setUsageLimit(request.usageLimit());
        }
        if (request.status() != null && !request.status().isBlank()) {
            promotion.setStatus(normalizeOrDefault(request.status(), promotion.getStatus(), PROMOTION_STATUSES));
        }
        if (request.startsAt() != null) {
            promotion.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            promotion.setEndsAt(request.endsAt());
        }

        Promotion saved = promotionRepository.save(promotion);
        writeAudit("cập nhật campaign", saved.getCode());
        return toPromotionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminAccountResponse> getAccounts(String type) {
        String normalizedType = normalizeNullable(type);

        return userRepository.findAll().stream()
            .map(this::toAccountResponse)
            .filter(account -> normalizedType == null || normalizedType.equals(account.accountType()))
            .sorted(Comparator.comparing(AdminAccountResponse::createdAt).reversed())
            .toList();
    }

    @Override
    @Transactional
    public AdminAccountResponse createAccount(AdminAccountCreateRequest request) {
        String email = normalizeRequired(request.email(), "Email").toLowerCase(Locale.ROOT);
        String username = request.username() == null || request.username().isBlank()
            ? email.substring(0, email.indexOf('@'))
            : request.username().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        String normalizedRole = normalizeRole(request.role());
        Role role = roleRepository.findByName(normalizedRole)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role not found: " + normalizedRole));

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(
            request.password() == null || request.password().isBlank() ? "ChangeMe@123" : request.password()
        ));
        applyName(user, normalizeRequired(request.name(), "Name"));
        user.setVerified(Boolean.TRUE.equals(request.verified()));
        user.setAccountStatus(normalizeOrDefault(request.status(), "ACTIVE", ACCOUNT_STATUSES));
        user.setActive("ACTIVE".equals(user.getAccountStatus()));
        user.setRoles(new HashSet<>(Set.of(role)));

        User saved = userRepository.save(user);
        writeAudit("tạo tài khoản", saved.getEmail());
        return toAccountResponse(saved);
    }

    @Override
    @Transactional
    public AdminAccountResponse updateAccountRole(Long id, AdminAccountRoleUpdateRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        String normalizedRole = normalizeRole(request.role());
        Role role = roleRepository.findByName(normalizedRole)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role not found: " + normalizedRole));

        user.setRoles(new HashSet<>(Set.of(role)));
        User saved = userRepository.save(user);
        writeAudit("cập nhật quyền", displayName(saved) + " thành " + normalizedRole);
        return toAccountResponse(saved);
    }

    @Override
    @Transactional
    public AdminAccountResponse updateAccountStatus(Long id, AdminAccountStatusUpdateRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        String status = normalizeOrDefault(request.status(), "ACTIVE", ACCOUNT_STATUSES);

        user.setAccountStatus(status);
        user.setActive("ACTIVE".equals(status));
        User saved = userRepository.save(user);
        writeAudit("cập nhật trạng thái tài khoản", displayName(saved) + " thành " + status);
        return toAccountResponse(saved);
    }

    @Override
    @Transactional
    public void deleteAccount(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        String target = displayName(user);
        userRepository.delete(user);
        writeAudit("xóa tài khoản", target);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionResponse> getRoles() {
        return roleRepository.findAll().stream()
            .sorted(Comparator.comparing(Role::getName))
            .map(role -> new RolePermissionResponse(
                role.getName(),
                roleTitle(role.getName()),
                role.getDescription(),
                role.getPermissions().stream()
                    .map(Permission::getCode)
                    .sorted()
                    .toList()
            ))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> getAuditLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
            .map(this::toAuditLogResponse)
            .toList();
    }

    private PricingRuleResponse toPricingRuleResponse(PricingRule rule) {
        return new PricingRuleResponse(
            rule.getId(),
            rule.getCode(),
            rule.getName(),
            rule.getDescription(),
            rule.getAmount(),
            rule.getUnit(),
            rule.getStatus(),
            rule.getEffectiveFrom(),
            rule.getUpdatedAt()
        );
    }

    private PromotionResponse toPromotionResponse(Promotion promotion) {
        return new PromotionResponse(
            promotion.getId(),
            promotion.getCode(),
            promotion.getCampaignName(),
            promotion.getDiscountType(),
            promotion.getDiscountValue(),
            promotion.getMaxDiscountAmount(),
            promotion.getUsageCount(),
            promotion.getUsageLimit(),
            promotion.getStatus(),
            promotion.getStartsAt(),
            promotion.getEndsAt(),
            promotion.getUpdatedAt()
        );
    }

    private AdminAccountResponse toAccountResponse(User user) {
        List<String> roles = user.getRoles().stream()
            .map(Role::getName)
            .sorted()
            .toList();
        String primaryRole = roles.isEmpty() ? "CUSTOMER" : roles.get(0);
        String accountType = "CUSTOMER".equals(primaryRole) ? "CUSTOMER" : "ADMIN";

        return new AdminAccountResponse(
            user.getId(),
            displayName(user),
            user.getUsername(),
            user.getEmail(),
            accountType,
            primaryRole,
            normalizeOrDefault(user.getAccountStatus(), user.getActive() ? "ACTIVE" : "INACTIVE", ACCOUNT_STATUSES),
            user.getVerified(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            roles
        );
    }

    private AdminAuditLogResponse toAuditLogResponse(AdminAuditLog log) {
        return new AdminAuditLogResponse(
            log.getId(),
            log.getActor(),
            log.getAction(),
            log.getTarget(),
            log.getIpAddress(),
            log.getCreatedAt()
        );
    }

    private void writeAudit(String action, String target) {
        AdminAuditLog log = new AdminAuditLog();
        log.setActor(currentActor());
        log.setAction(action);
        log.setTarget(target);
        log.setIpAddress("system");
        auditLogRepository.save(log);
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "System";
        }
        return authentication.getName();
    }

    private String normalizeRole(String role) {
        String normalized = normalizeOrDefault(role, "CUSTOMER", ACCOUNT_ROLES);
        return "SUPER_ADMIN".equals(normalized) ? "ADMIN" : normalized;
    }

    private String normalizeOrDefault(String value, String defaultValue, Set<String> allowedValues) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            normalized = defaultValue;
        }
        if (!allowedValues.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported value: " + value);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is required");
        }
        return value.trim();
    }

    private BigDecimal validatePositive(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be greater than zero");
        }
        return value;
    }

    private void applyName(User user, String name) {
        String[] parts = name.trim().split("\\s+", 2);
        user.setFirstName(parts[0]);
        user.setLastName(parts.length > 1 ? parts[1] : "");
    }

    private String displayName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? user.getUsername() : fullName;
    }

    private String roleTitle(String role) {
        return switch (role) {
            case "ADMIN" -> "Super admin";
            case "OPERATOR" -> "Operator";
            case "SUPPORT" -> "Support";
            case "MAINTENANCE" -> "Maintenance";
            case "MANAGER" -> "Manager";
            case "CUSTOMER" -> "Người dùng";
            default -> role;
        };
    }
}
