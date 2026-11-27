package com.ebike.service.auth.impl;

import com.ebike.dto.auth.response.AuthResponse;
import com.ebike.event.notification.UserRegisteredEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.dto.auth.request.ChangePasswordRequest;
import com.ebike.dto.auth.request.LoginRequest;
import com.ebike.dto.auth.request.RegisterRequest;
import com.ebike.dto.auth.request.UpdateProfileRequest;
import com.ebike.dto.auth.response.UserProfileResponse;
import com.ebike.model.entity.auth.AuthenticationLog;
import com.ebike.model.entity.auth.Role;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.AuthenticationLogRepository;
import com.ebike.repository.auth.RoleRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.service.auth.AuthenticationService;
import com.ebike.service.auth.JwtTokenProvider;
import com.ebike.service.auth.PermissionService;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final String DEFAULT_CUSTOMER_ROLE = "CUSTOMER";
    private static final java.util.regex.Pattern EMAIL_PATTERN =
        java.util.regex.Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final String LOGIN_FAILED_MESSAGE = "Tên đăng nhập hoặc mật khẩu chưa chính xác.";

    // hash checked for unknown usernames so that a refused login costs about the same as one for a real account
    private volatile String dummyPasswordHash;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationLogRepository authenticationLogRepository;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    private final com.ebike.service.admin.AuditLogService auditLog;

    public AuthenticationServiceImpl(
        UserRepository userRepository,
        RoleRepository roleRepository,
        AuthenticationLogRepository authenticationLogRepository,
        PermissionService permissionService,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider,
        com.ebike.service.admin.AuditLogService auditLog
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authenticationLogRepository = authenticationLogRepository;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLog = auditLog;
    }

    // Staff sign-ins go to the audit trail (customers would flood it); a refused attempt on a staff account is recorded too.
    private void auditSignIn(User user, boolean success, String reason) {
        boolean staff = user.getRoles().stream().anyMatch(role -> !"CUSTOMER".equals(role.getName()));
        if (staff) {
            auditLog.recordBy(user.getUsername(), success ? "đăng nhập" : "đăng nhập thất bại", success ? user.getUsername() : reason);
        }
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        if (userRepository.existsByUsername(request.username().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã được sử dụng.");
        }
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }

        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password().trim()));
        user.setFirstName(trimToNull(request.firstName()));
        user.setLastName(trimToNull(request.lastName()));
        user.setActive(true);
        user.setVerified(false);

        Role defaultRole = roleRepository.findByName(DEFAULT_CUSTOMER_ROLE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống chưa có vai trò mặc định."));
        user.getRoles().add(defaultRole);

        // Guest orders with this email are NOT attached here: the email is unverified at sign-up.
        // Customers attach them later via POST /api/v1/orders/guest-orders/link after an OTP check.
        User savedUser = userRepository.save(user);
        // the welcome email goes out after the commit, from the mail pool; a failure there never undoes the sign-up
        NotificationEventBridge.publish(new UserRegisteredEvent(savedUser.getId()));
        return toAuthResponse(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        if (request == null || isBlank(request.usernameOrEmail()) || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên đăng nhập hoặc email và mật khẩu.");
        }

        User user = findLoginUser(request.usernameOrEmail());
        if (user == null) {
            // Same answer and similar timing as a wrong password, so nobody can tell which usernames exist.
            passwordEncoder.matches(request.password(), dummyPasswordHash());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            auditSignIn(user, false, "tài khoản bị khóa");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditSignIn(user, false, "sai mật khẩu");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
        }
        auditSignIn(user, true, null);

        AuthenticationLog log = new AuthenticationLog();
        log.setUser(user);
        log.setLoginTime(LocalDateTime.now());
        log.setIpAddress(trimToNull(ipAddress));
        log.setUserAgent(trimToNull(userAgent));
        authenticationLogRepository.save(log);

        return toAuthResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String usernameOrEmail) {
        if (isBlank(usernameOrEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên đăng nhập hoặc email.");
        }

        User user = findUser(usernameOrEmail);
        return toUserProfileResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(String usernameOrEmail, UpdateProfileRequest request) {
        if (isBlank(usernameOrEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên đăng nhập hoặc email.");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu nội dung yêu cầu.");
        }

        User user = findUser(usernameOrEmail);
        user.setFirstName(trimToNull(request.firstName()));
        user.setLastName(trimToNull(request.lastName()));
        String requestedEmail = trimToNull(request.email());
        if (requestedEmail != null) {
            String normalizedEmail = requestedEmail.toLowerCase(Locale.ROOT);
            if (!normalizedEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(normalizedEmail)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
            }
            user.setEmail(normalizedEmail);
        }
        User savedUser = userRepository.save(user);
        return toUserProfileResponse(savedUser);
    }

    @Override
    public void changePassword(String usernameOrEmail, ChangePasswordRequest request) {
        if (isBlank(usernameOrEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên đăng nhập hoặc email.");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu nội dung yêu cầu.");
        }

        String currentPassword = trimToNull(request.currentPassword());
        String newPassword = trimToNull(request.newPassword());
        String confirmPassword = trimToNull(request.confirmPassword());

        if (currentPassword == null || newPassword == null || confirmPassword == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập mật khẩu hiện tại, mật khẩu mới và nhập lại mật khẩu mới.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu nhập lại không khớp.");
        }
        if (newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới cần ít nhất 6 ký tự.");
        }
        if (newPassword.equals(currentPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        User user = findUser(usernameOrEmail);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private User findLoginUser(String usernameOrEmail) {
        String trimmed = usernameOrEmail.trim();
        return userRepository.findByEmail(trimmed.toLowerCase(Locale.ROOT))
            .or(() -> userRepository.findByUsername(trimmed))
            .orElse(null);
    }

    private String dummyPasswordHash() {
        String hash = dummyPasswordHash;
        if (hash == null) {
            hash = passwordEncoder.encode("dummy-password-for-timing");
            dummyPasswordHash = hash;
        }
        return hash;
    }

    private User findUser(String usernameOrEmail) {
        String trimmed = usernameOrEmail.trim();
        return userRepository.findByEmail(trimmed.toLowerCase(Locale.ROOT))
            .or(() -> userRepository.findByUsername(trimmed))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng."));
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu nội dung yêu cầu.");
        }
        if (isBlank(request.username()) || isBlank(request.email()) || isBlank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập tên đăng nhập, email và mật khẩu.");
        }
        if (request.password().trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có ít nhất 6 ký tự.");
        }
        // the welcome email goes to this address, so it must at least look like one (checkout applies the same rule)
        if (!EMAIL_PATTERN.matcher(request.email().trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ.");
        }
    }

    private AuthResponse toAuthResponse(User user) {
        Set<String> roles = extractRoleNames(user);
        String jwtToken = jwtTokenProvider.generateToken(user);
        return new AuthResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            buildFullName(user),
            jwtToken,
            roles
        );
    }

    private UserProfileResponse toUserProfileResponse(User user) {
        return new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getActive(),
            user.getVerified(),
            extractRoleNames(user),
            permissionService.resolvePermissionCodes(user)
        );
    }

    private Set<String> extractRoleNames(User user) {
        return user.getRoles().stream()
            .map(Role::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String buildFullName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName();
        String lastName = user.getLastName() == null ? "" : user.getLastName();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? user.getUsername() : fullName;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
