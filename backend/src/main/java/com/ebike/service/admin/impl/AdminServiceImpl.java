package com.ebike.service.admin.impl;

import com.ebike.common.validation.AccountFieldRules;
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
import com.ebike.model.entity.admin.AdminAuditLog;
import com.ebike.model.entity.admin.PricingRule;
import com.ebike.model.entity.admin.Promotion;
import com.ebike.repository.admin.AdminAuditLogRepository;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.admin.PricingService;
import com.ebike.service.admin.AdminService;
import com.ebike.model.entity.auth.Permission;
import com.ebike.model.entity.auth.Role;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.RoleRepository;
import com.ebike.repository.auth.UserRepository;
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
    private static final Set<String> ACCOUNT_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "MANAGER", "OPERATOR", "SUPPORT", "MAINTENANCE", "CUSTOMER");

    private static final BigDecimal MAX_DELIVERY_FEE = new BigDecimal("5000000");

    private final PricingRuleRepository pricingRuleRepository;
    private final PromotionRepository promotionRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderRepository orderRepository;
    private final com.ebike.repository.auth.AuthenticationLogRepository authenticationLogRepository;
    private final com.ebike.repository.testride.TestRideBookingRepository testRides;
    private final com.ebike.service.product.ProductReviewService reviews;
    private static final java.util.regex.Pattern ACCOUNT_EMAIL_PATTERN = java.util.regex.Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private final AuditLogService auditLog;

    public AdminServiceImpl(
        PricingRuleRepository pricingRuleRepository,
        PromotionRepository promotionRepository,
        AdminAuditLogRepository auditLogRepository,
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder,
        OrderRepository orderRepository,
        com.ebike.repository.auth.AuthenticationLogRepository authenticationLogRepository,
        com.ebike.repository.testride.TestRideBookingRepository testRides,
        com.ebike.service.product.ProductReviewService reviews,
        AuditLogService auditLog
    ) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.promotionRepository = promotionRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.orderRepository = orderRepository;
        this.authenticationLogRepository = authenticationLogRepository;
        this.testRides = testRides;
        this.reviews = reviews;
        this.auditLog = auditLog;
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy quy tắc giá."));

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền không được âm.");
        }
        if (rule.getCode().startsWith(PricingService.INSTALLMENT_RATE_PREFIX) && request.amount().compareTo(new BigDecimal("60")) > 0) {
            // V24: a yearly rate above 60% is a typo (an amount in dong), and customers would be shown it
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lãi suất trả góp không được vượt 60% mỗi năm.");
        }

        String ruleCode = rule.getCode();
        if (PricingService.REGISTRATION_SERVICE.equals(ruleCode) && request.amount().signum() <= 0) {
            // a zero fee while the service is offered would silently give it away
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phí dịch vụ hồ sơ phải lớn hơn 0.");
        }
        if ((PricingService.REGISTRATION_TAX_RATE_CENTRAL.equals(ruleCode) || PricingService.REGISTRATION_TAX_RATE_OTHER.equals(ruleCode))
            && request.amount().compareTo(new BigDecimal("100")) > 0) {
            // a rate is a percentage: 150 is an amount in dong typed in the wrong box
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thuế trước bạ phải nằm trong khoảng 0-100%.");
        }
        if ((PricingService.HOME_DELIVERY_SAME_CITY.equals(ruleCode) || PricingService.HOME_DELIVERY_OTHER_PROVINCE.equals(ruleCode))
            && request.amount().compareTo(MAX_DELIVERY_FEE) > 0) {
            // owner decision D5: a delivery fee above 5,000,000 per bike is a typo
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phí giao xe không được vượt quá 5.000.000 ₫ mỗi xe.");
        }

        if (rule.getCode().startsWith(PricingService.SAVINGS_PREFIX)) {
            // V33: the savings calculator divides by nothing but shows these figures to every visitor, so a zero or a typo in the wrong unit is refused here
            BigDecimal ceiling = savingsCeiling(rule.getCode());
            BigDecimal floor = savingsFloor(rule.getCode());
            if (request.amount().stripTrailingZeros().scale() > 2) {
                // the column keeps two decimals: 1.855 would be saved as 1.86 without a word
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá trị chỉ được có tối đa 2 chữ số thập phân.");
            }
            if (request.amount().compareTo(floor) < 0 || request.amount().compareTo(ceiling) > 0) {
                // a price typed with a thousands dot (2.900) arrives as 2.9: the floor catches it instead of showing every visitor a price of 2.9 dong
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá trị phải từ " + floor.toPlainString() + " đến " + ceiling.toPlainString() + " (" + rule.getUnit() + "). Nhập số không có dấu chấm ngăn cách hàng nghìn.");
            }
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

    /** 0 (or less) means "no cap"; shared by create and update so a negative cap can never be stored. */
    private static BigDecimal normalizeMaxDiscount(BigDecimal value) {
        return value == null || value.signum() <= 0 ? null : value;
    }

    @Override
    @Transactional
    public PromotionResponse createPromotion(PromotionCreateRequest request) {
        String code = normalizeRequired(request.code(), "Mã khuyến mãi").replaceAll("\\s+", "");
        if (promotionRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã khuyến mãi đã tồn tại.");
        }

        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setCampaignName(normalizeRequired(request.campaignName(), "Tên chiến dịch"));
        promotion.setDiscountType(normalizeOrDefault(request.discountType(), "PERCENTAGE", DISCOUNT_TYPES));
        promotion.setDiscountValue(validatePositive(request.discountValue(), "Giá trị giảm"));
        promotion.setMaxDiscountAmount(normalizeMaxDiscount(request.maxDiscountAmount()));
        promotion.setUsageLimit(request.usageLimit() == null || request.usageLimit() < 0 ? 0 : request.usageLimit());
        promotion.setUsageCount(0);
        promotion.setStatus(normalizeOrDefault(request.status(), "WAITING", PROMOTION_STATUSES));
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        validatePromotion(promotion);

        Promotion saved = promotionRepository.save(promotion);
        writeAudit("tạo campaign", saved.getCode());
        return toPromotionResponse(saved);
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(Long id, PromotionUpdateRequest request) {
        Promotion promotion = promotionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khuyến mãi."));

        if (request.campaignName() != null && !request.campaignName().isBlank()) {
            promotion.setCampaignName(request.campaignName().trim());
        }
        if (request.discountType() != null && !request.discountType().isBlank()) {
            promotion.setDiscountType(normalizeOrDefault(request.discountType(), promotion.getDiscountType(), DISCOUNT_TYPES));
        }
        if (request.discountValue() != null) {
            promotion.setDiscountValue(validatePositive(request.discountValue(), "Giá trị giảm"));
        }
        if (request.maxDiscountAmount() != null) {
            promotion.setMaxDiscountAmount(normalizeMaxDiscount(request.maxDiscountAmount()));
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
        validatePromotion(promotion);

        Promotion saved = promotionRepository.save(promotion);
        writeAudit("cập nhật campaign", saved.getCode());
        return toPromotionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminAccountResponse> getAccounts(String type) {
        return getAccounts(type, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminAccountResponse> getAccounts(String type, String search) {
        String normalizedType = normalizeNullable(type);
        String needle = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);

        // Accounts the customer asked to delete (DELETED) are hidden: only an anonymised shell remains for the orders.
        return userRepository.findAll().stream()
            .filter(user -> !AccountErasure.DELETED.equals(user.getAccountStatus()))
            .map(this::toAccountResponse)
            .filter(account -> normalizedType == null || normalizedType.equals(account.accountType()))
            .filter(account -> needle == null
                || (account.name() != null && account.name().toLowerCase(Locale.ROOT).contains(needle))
                || (account.username() != null && account.username().toLowerCase(Locale.ROOT).contains(needle))
                || (account.email() != null && account.email().toLowerCase(Locale.ROOT).contains(needle)))
            .sorted(Comparator.comparing(AdminAccountResponse::createdAt).reversed())
            .toList();
    }

    @Override
    @Transactional
    public AdminAccountResponse createAccount(AdminAccountCreateRequest request) {
        String email = AccountFieldRules.email(request.email());
        String username = request.username() == null || request.username().isBlank()
            ? AccountFieldRules.usernameFromEmail(email)
            : AccountFieldRules.username(request.username());
        String name = normalizeRequired(request.name(), "Tên");
        assertNameParts(name);

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã được sử dụng.");
        }

        String normalizedRole = normalizeRole(request.role());
        Role role = roleRepository.findByName(normalizedRole)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy vai trò: " + normalizedRole));

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(AccountFieldRules.password(request.password())));
        applyName(user, name);
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
        assertNotDeleted(user);
        String normalizedRole = normalizeRole(request.role());
        Role role = roleRepository.findByName(normalizedRole)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy vai trò: " + normalizedRole));

        if (!"ADMIN".equals(normalizedRole)) {
            assertNotSelf(user, "Bạn không thể tự đổi vai trò của chính mình.");
            assertNotLastAdmin(user, "Đây là quản trị viên cuối cùng đang hoạt động, không thể bỏ quyền quản trị.");
        }
        user.setRoles(new HashSet<>(Set.of(role)));
        User saved = userRepository.save(user);
        writeAudit("cập nhật quyền", displayName(saved) + " thành " + normalizedRole);
        return toAccountResponse(saved);
    }

    @Override
    @Transactional
    public AdminAccountResponse updateAccountStatus(Long id, AdminAccountStatusUpdateRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
        assertNotDeleted(user);
        String status = normalizeOrDefault(request.status(), "ACTIVE", ACCOUNT_STATUSES);

        if (!"ACTIVE".equals(status)) {
            assertNotSelf(user, "Bạn không thể tự vô hiệu hóa tài khoản của chính mình.");
            assertNotLastAdmin(user, "Đây là quản trị viên cuối cùng đang hoạt động, không thể vô hiệu hóa.");
        }
        user.setAccountStatus(status);
        user.setActive("ACTIVE".equals(status));
        User saved = userRepository.save(user);
        writeAudit("cập nhật trạng thái tài khoản", displayName(saved) + " thành " + status);
        return toAccountResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public com.ebike.dto.admin.response.AdminAccountDetail getAccountDetail(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
        assertNotDeleted(user);
        return toAccountDetail(user);
    }

    @Override
    @Transactional
    public com.ebike.dto.admin.response.AdminAccountDetail updateAccount(Long id, com.ebike.dto.admin.request.AdminAccountUpdateRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
        assertNotDeleted(user);
        String name = normalizeRequired(request == null ? null : request.name(), "Tên");
        assertNameParts(name);
        String email = normalizeRequired(request.email(), "Email").toLowerCase(Locale.ROOT);
        if (email.length() > 150 || !ACCOUNT_EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ.");
        }
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        applyName(user, name);
        user.setEmail(email);
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        writeAudit("sửa thông tin tài khoản", saved.getEmail());
        return toAccountDetail(saved);
    }

    private com.ebike.dto.admin.response.AdminAccountDetail toAccountDetail(User user) {
        AdminAccountResponse row = toAccountResponse(user);
        long orders = orderRepository.countByUserId(user.getId());
        return new com.ebike.dto.admin.response.AdminAccountDetail(
            user.getId(), user.getFirstName(), user.getLastName(), user.getUsername(), user.getEmail(),
            row.accountType(), row.status(), user.getVerified(), row.roles(), user.getCreatedAt(), user.getUpdatedAt(),
            orders, orders == 0
        );
    }

    // READ_COMMITTED: see OwnAccountService.deleteOwnAccount (the erasure's count of open applications must see what is committed once it holds the lock)
    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteAccount(Long id, String confirmEmail) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
        assertNotSelf(user, "Bạn không thể tự xóa tài khoản của chính mình.");
        assertNotLastAdmin(user, "Đây là quản trị viên cuối cùng đang hoạt động, không thể xóa.");
        assertNotDeleted(user);
        // Staff who appear in the audit trail are kept: the trail must keep saying who did what. Lock them instead.
        boolean staff = user.getRoles().stream().anyMatch(role -> !"CUSTOMER".equals(role.getName()));
        if (staff && auditLogRepository.existsByActorIn(java.util.List.of(user.getUsername(), user.getEmail()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tài khoản nhân viên này đã có thao tác trong nhật ký nên không thể xóa. Hãy vô hiệu hóa tài khoản.");
        }
        // Erasing is irreversible, so the admin must type the account's email to confirm (checked here, not only in the form).
        if (confirmEmail == null || !confirmEmail.trim().equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email xác nhận không khớp với tài khoản cần xóa.");
        }
        // Orders and payments are the shop's accounting record: an account that has placed orders is not deleted (disable it instead).
        if (orderRepository.existsByUserId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tài khoản này đã có đơn hàng nên không thể xóa. Hãy vô hiệu hóa tài khoản.");
        }
        // A booked test ride must not vanish from the showroom's schedule with the account: the customer cancels it, or the admin disables the account.
        if (testRides.existsByUserIdAndStatusIn(id, java.util.List.of("REQUESTED", "CONFIRMED"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tài khoản này còn lịch lái thử chưa kết thúc nên không thể xóa. Hãy vô hiệu hóa tài khoản.");
        }
        try {
            // the reviews go with the account; the ratings shown on the products are recounted without them
            reviews.removeReviewsOf(id);
            authenticationLogRepository.deleteByUserId(id);
            userRepository.delete(user);
            userRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Tài khoản này còn dữ liệu liên quan nên không thể xóa. Hãy vô hiệu hóa tài khoản.");
        }
        // The audit entry must not keep the personal data that was just deleted.
        writeAudit("xóa tài khoản", "tài khoản #" + id);
    }

    private void assertNotDeleted(User user) {
        if (AccountErasure.DELETED.equals(user.getAccountStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản này đã bị xóa và không thể thay đổi.");
        }
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
        auditLog.record(action, target);
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "System";
        }
        return authentication.getName();
    }

    /** Checks the code as a whole, so an edit that is fine on its own cannot leave a broken code behind. */
    private void validatePromotion(Promotion promotion) {
        if ("PERCENTAGE".equals(promotion.getDiscountType()) && promotion.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giảm theo phần trăm không được vượt quá 100%.");
        }
        if (promotion.getStartsAt() != null && promotion.getEndsAt() != null && promotion.getEndsAt().isBefore(promotion.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu.");
        }
    }

    private void assertNameParts(String name) {
        String[] nameParts = name.trim().split("\\s+", 2);
        if (nameParts[0].length() > AccountFieldRules.MAX_NAME_LENGTH
            || (nameParts.length > 1 && nameParts[1].length() > AccountFieldRules.MAX_NAME_LENGTH)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ và tên đệm/tên mỗi phần tối đa 100 ký tự.");
        }
    }

    private void assertNotSelf(User target, String message) {
        String actor = currentActor();
        if (actor.equalsIgnoreCase(target.getUsername()) || actor.equalsIgnoreCase(target.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    /** Someone must always be able to sign in as an admin, or nobody can fix the shop. */
    private void assertNotLastAdmin(User target, String message) {
        if (!isActiveAdmin(target)) {
            return;
        }
        boolean anotherAdmin = userRepository.findAll().stream()
            .filter(candidate -> !candidate.getId().equals(target.getId()))
            .anyMatch(this::isActiveAdmin);
        if (!anotherAdmin) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private boolean isActiveAdmin(User user) {
        return Boolean.TRUE.equals(user.getActive())
            && user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName()) || "SUPER_ADMIN".equals(role.getName()));
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá trị không được hỗ trợ: " + value);
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " là bắt buộc");
        }
        return value.trim();
    }

    private BigDecimal validatePositive(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " phải lớn hơn 0");
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
            case "CUSTOMER" -> "Customer";
            default -> role;
        };
    }

    /** V33: the largest value an admin may type for a rule of the savings calculator (a typo guard, well above any real figure). */
    private static BigDecimal savingsFloor(String code) {
        if (PricingService.SAVINGS_ELECTRICITY_PRICE.equals(code)) {
            return new BigDecimal("500");
        }
        if (PricingService.SAVINGS_PETROL_PRICE.equals(code)) {
            return new BigDecimal("5000");
        }
        return new BigDecimal("0.5");
    }

    /** V33: the largest value an admin may type for a rule of the savings calculator (a typo guard, well above any real figure). */
    private static BigDecimal savingsCeiling(String code) {
        if (PricingService.SAVINGS_ELECTRICITY_PRICE.equals(code)) {
            return new BigDecimal("20000");
        }
        if (PricingService.SAVINGS_PETROL_PRICE.equals(code)) {
            return new BigDecimal("200000");
        }
        return new BigDecimal("10");
    }
}
