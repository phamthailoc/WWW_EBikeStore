package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.auth.request.ChangePasswordRequest;
import com.ebike.dto.auth.request.LoginRequest;
import com.ebike.dto.auth.request.RegisterRequest;
import com.ebike.dto.auth.request.UpdateProfileRequest;
import com.ebike.dto.auth.response.AuthResponse;
import com.ebike.model.entity.auth.AuthenticationLog;
import com.ebike.model.entity.auth.Role;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.AuthenticationLogRepository;
import com.ebike.repository.auth.RoleRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.auth.JwtTokenProvider;
import com.ebike.service.auth.PermissionService;
import com.ebike.service.auth.impl.AuthenticationServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthenticationServiceTest {

    UserRepository users = mock(UserRepository.class);
    RoleRepository roles = mock(RoleRepository.class);
    AuthenticationLogRepository logs = mock(AuthenticationLogRepository.class);
    PermissionService permissions = mock(PermissionService.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    AuditLogService audit = mock(AuditLogService.class);
    AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationServiceImpl(users, roles, logs, permissions, encoder, jwt, audit);
        when(encoder.encode(anyString())).thenAnswer(inv -> "ENC(" + inv.getArgument(0) + ")");
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(77L);
            }
            return u;
        });
        when(roles.findByName("CUSTOMER")).thenReturn(Optional.of(Fx.role("CUSTOMER")));
        when(jwt.generateToken(any())).thenReturn("jwt-token");
    }

    private static RegisterRequest reg(String username, String email, String password, String first, String last) {
        return new RegisterRequest(username, email, password, first, last);
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(status.value());
    }

    // ------------------------------------------------------------------ register

    @Test
    void register_happyPath_createsActiveUnverifiedCustomerWithHashedPassword() {
        AuthResponse res = service.register(reg("  tien  ", "  Tien@Example.COM ", "secret12", " Le ", " Tien "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User u = saved.getValue();
        assertThat(u.getUsername()).isEqualTo("tien");
        assertThat(u.getEmail()).isEqualTo("tien@example.com");
        assertThat(u.getFirstName()).isEqualTo("Le");
        assertThat(u.getLastName()).isEqualTo("Tien");
        assertThat(u.getActive()).isTrue();
        assertThat(u.getVerified()).isFalse();
        assertThat(u.getPasswordHash()).isEqualTo("ENC(secret12)").doesNotContain("secret12".concat("x"));
        assertThat(u.getRoles()).extracting(Role::getName).containsExactly("CUSTOMER");
        assertThat(res.token()).isEqualTo("jwt-token");
        assertThat(res.roles()).containsExactly("CUSTOMER");
        assertThat(res.fullName()).isEqualTo("Le Tien");
    }

    @Test
    void register_neverStoresPlainPassword() {
        service.register(reg("tien", "t@x.com", "plainpass", null, null));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("plainpass");
        verify(encoder).encode("plainpass");
    }

    @Test
    void register_noNames_fullNameFallsBackToUsername() {
        AuthResponse res = service.register(reg("tien", "t@x.com", "secret12", "  ", null));
        assertThat(res.fullName()).isEqualTo("tien");
    }

    @Test
    void register_nullRequest_is400() {
        assertThatThrownBy(() -> service.register(null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t"})
    void register_blankUsername_is400(String username) {
        assertThatThrownBy(() -> service.register(reg(username, "a@b.com", "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void register_blankEmail_is400(String email) {
        assertThatThrownBy(() -> service.register(reg("tien", email, "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "      "})
    void register_blankPassword_is400(String password) {
        assertThatThrownBy(() -> service.register(reg("tien", "a@b.com", password, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void register_passwordFiveCharacters_rejected() {
        assertThatThrownBy(() -> service.register(reg("tien", "a@b.com", "12345", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void register_passwordOfOnlySpacesPaddedTo6_rejectedBecauseTrimmed() {
        assertThatThrownBy(() -> service.register(reg("tien", "a@b.com", "  abc  ", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void register_passwordExactlyEight_acceptedByServerMinimum() {
        service.register(reg("tien", "a@b.com", "abcdefgh", null, null));
        verify(users).save(any(User.class));
    }

    /**
     * Signup page (templates/store/signup.html: minlength="8") requires 8 characters but the server only checks 6.
     * Expected: a 7-character password is refused with 400 (the form and the API must agree). Actual: accepted.
     * AuthenticationServiceImpl.java:~252 (validateRegisterRequest: length() < 6).
     */
    @Test
    void regression_register_acceptsSevenCharacterPasswordThoughSignupFormRequiresEight() {
        assertThatThrownBy(() -> service.register(reg("tien", "a@b.com", "abcdefg", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainaddress", "a@b", "@b.com", "a@.com", "a b@c.com", "a@b.c", "a@@b.com", "a@b..", "a@b.com."})
    void register_badEmail_is400(String email) {
        assertThatThrownBy(() -> service.register(reg("tien", email, "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b.co", "first.last+tag@sub.example.org", "UPPER@EXAMPLE.COM", "a_b%c@x-y.io"})
    void register_validEmails_accepted(String email) {
        service.register(reg("tien" + email.length(), email, "secret12", null, null));
        verify(users).save(any(User.class));
    }

    @Test
    void register_duplicateUsername_is409_andNothingSaved() {
        when(users.existsByUsername("tien")).thenReturn(true);
        assertThatThrownBy(() -> service.register(reg(" tien ", "a@b.com", "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(users, never()).save(any());
    }

    @Test
    void register_duplicateEmailDifferentCaseAndPadding_is409() {
        when(users.existsByEmail("dup@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(reg("tien", "  DUP@Example.com  ", "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(users, never()).save(any());
    }

    @Test
    void register_missingCustomerRole_is500NotSilentlyStaff() {
        when(roles.findByName("CUSTOMER")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.register(reg("tien", "a@b.com", "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.INTERNAL_SERVER_ERROR));
        verify(users, never()).save(any());
    }

    @Test
    void register_alwaysAssignsOnlyCustomerRole_evenIfRequestSmugglesNothing() {
        service.register(reg("tien", "a@b.com", "secret12", null, null));
        verify(roles).findByName("CUSTOMER");
        verify(roles, never()).findByName("ADMIN");
    }

    /**
     * users.first_name is VARCHAR(100) (User.java:42). The service has no length check, so a 300-character first name reaches the
     * database and the request ends as HTTP 500. Expected: 400 before saving. Actual: the user is saved.
     * AuthenticationServiceImpl.java:~87 (register copies trimToNull(firstName) without a length check).
     */
    @Test
    void regression_register_firstNameOf300CharactersMustBeRefusedWith400() {
        String longName = "A".repeat(300);
        assertThatThrownBy(() -> service.register(reg("tien", "a@b.com", "secret12", longName, "Tien")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    @Test
    void regression_register_lastNameOf101CharactersMustBeRefusedWith400() {
        assertThatThrownBy(() -> service.register(reg("tien", "a@b.com", "secret12", "Le", "B".repeat(101))))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    /** username column is VARCHAR(100) (User.java:35): 101 characters would be a database error, i.e. HTTP 500. */
    @Test
    void regression_register_usernameLongerThan100MustBeRefusedWith400() {
        assertThatThrownBy(() -> service.register(reg("u".repeat(101), "a@b.com", "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    /** email column is VARCHAR(255): a 300-character (otherwise valid) email must be a 400, not a database error. */
    @Test
    void regression_register_emailLongerThan255MustBeRefusedWith400() {
        String email = "a".repeat(300) + "@example.com";
        assertThatThrownBy(() -> service.register(reg("tien", email, "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    /**
     * Black-box finding: username "qa user x" (with spaces) is accepted. A login name with inner spaces (or "@", which makes it
     * ambiguous with an email in findUser) should be refused with 400. Actual: accepted and saved.
     * AuthenticationServiceImpl.java:~83 (only trim() is applied to the username).
     */
    @Test
    void regression_register_usernameWithSpacesMustBeRefused() {
        assertThatThrownBy(() -> service.register(reg("qa user x", "a@b.com", "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    /** A username that looks like another person's email would shadow that email at login (findUser tries the email first). */
    @Test
    void regression_register_usernameThatLooksLikeAnEmailMustBeRefused() {
        assertThatThrownBy(() -> service.register(reg("victim@example.com", "attacker@example.com", "secret12", null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    // ------------------------------------------------------------------ login

    private User customer() {
        User u = Fx.user(5L, "tien", "tien@example.com", "CUSTOMER");
        u.setFirstName("Le");
        u.setLastName("Tien");
        return u;
    }

    @Test
    void login_byEmail_success_recordsLogAndReturnsToken() {
        User u = customer();
        when(users.findByEmail("tien@example.com")).thenReturn(Optional.of(u));
        when(encoder.matches("secret12", u.getPasswordHash())).thenReturn(true);

        AuthResponse res = service.login(new LoginRequest("  TIEN@Example.com ", "secret12"), " 10.0.0.1 ", "JUnit");

        assertThat(res.userId()).isEqualTo(5L);
        assertThat(res.token()).isEqualTo("jwt-token");
        ArgumentCaptor<AuthenticationLog> log = ArgumentCaptor.forClass(AuthenticationLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().getUser()).isSameAs(u);
        assertThat(log.getValue().getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(log.getValue().getUserAgent()).isEqualTo("JUnit");
        assertThat(log.getValue().getLoginTime()).isNotNull();
    }

    @Test
    void login_byUsername_whenNoEmailMatch() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        when(encoder.matches("secret12", u.getPasswordHash())).thenReturn(true);
        assertThat(service.login(new LoginRequest(" tien ", "secret12"), null, null).username()).isEqualTo("tien");
    }

    @Test
    void login_customerSignIn_isNotWrittenToAuditTrail() {
        User u = customer();
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        service.login(new LoginRequest("tien@example.com", "secret12"), null, null);
        verify(audit, never()).recordBy(anyString(), anyString(), any());
    }

    @Test
    void login_staffSignIn_isWrittenToAuditTrail() {
        User u = Fx.user(9L, "boss", "boss@example.com", "MANAGER");
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        service.login(new LoginRequest("boss@example.com", "secret12"), null, null);
        verify(audit).recordBy(eq("boss"), eq("đăng nhập"), eq("boss"));
    }

    @Test
    void login_staffWrongPassword_isAuditedAsFailure_andIs401() {
        User u = Fx.user(9L, "boss", "boss@example.com", "ADMIN");
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.login(new LoginRequest("boss@example.com", "wrong"), null, null))
            .satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
        verify(audit).recordBy(eq("boss"), eq("đăng nhập thất bại"), eq("sai mật khẩu"));
        verify(logs, never()).save(any());
    }

    @Test
    void login_wrongPassword_is401_noLog_noToken() {
        User u = customer();
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.login(new LoginRequest("tien@example.com", "nope"), "1.1.1.1", "ua"))
            .satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
        verify(logs, never()).save(any());
        verify(jwt, never()).generateToken(any());
    }

    // Rewritten for B1b: the old rule (403 before the password is checked) let anyone confirm that a locked account exists.
    @Test
    void login_inactiveAccount_wrongPassword_is401_sameBodyAsUnknownUser() {
        User u = customer();
        u.setActive(false);
        when(users.findByEmail("tien@example.com")).thenReturn(Optional.of(u));
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(encoder.matches(anyString(), any())).thenReturn(false);
        Throwable locked = catchThrowable(() -> service.login(new LoginRequest("tien@example.com", "wrong"), null, null));
        Throwable unknown = catchThrowable(() -> service.login(new LoginRequest("ghost@example.com", "wrong"), null, null));
        assertStatus(locked, HttpStatus.UNAUTHORIZED);
        var lockedBody = new com.ebike.common.exception.ApiExceptionHandler().handleResponseStatusException((ResponseStatusException) locked);
        var unknownBody = new com.ebike.common.exception.ApiExceptionHandler().handleResponseStatusException((ResponseStatusException) unknown);
        assertThat(lockedBody.getStatusCode()).isEqualTo(unknownBody.getStatusCode());
        assertThat(lockedBody.getBody().message()).isEqualTo(unknownBody.getBody().message());
        assertThat(lockedBody.getBody().error()).isEqualTo(unknownBody.getBody().error());
        assertThat(lockedBody.getBody().status()).isEqualTo(unknownBody.getBody().status());
        verify(jwt, never()).generateToken(any());
    }

    @Test
    void login_inactiveAccount_correctPassword_is403() {
        User u = customer();
        u.setActive(false);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.login(new LoginRequest("tien@example.com", "secret12"), null, null))
            .satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN))
            .hasMessageContaining("Tài khoản đang bị khóa.");
        verify(jwt, never()).generateToken(any());
    }

    @Test
    void login_erasedAccount_isAlways401_evenIfThePasswordMatched() {
        User u = customer();
        u.setActive(false);
        u.setAccountStatus("DELETED");
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        for (boolean passwordMatches : new boolean[] {false, true}) {
            when(encoder.matches(anyString(), any())).thenReturn(passwordMatches);
            assertThatThrownBy(() -> service.login(new LoginRequest("tien@example.com", "secret12"), null, null))
                .satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED))
                .hasMessageContaining("Tên đăng nhập hoặc mật khẩu chưa chính xác.");
        }
        verify(jwt, never()).generateToken(any());
    }

    @Test
    void login_inactiveAccountWithCorrectPassword_neverGetsToken() {
        User u = customer();
        u.setActive(false);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        assertThatThrownBy(() -> service.login(new LoginRequest("tien@example.com", "secret12"), null, null))
            .isInstanceOf(ResponseStatusException.class);
        verify(jwt, never()).generateToken(any());
        verify(logs, never()).save(any());
    }

    @Test
    void login_nullActiveFlag_isTreatedAsLocked() {
        User u = customer();
        u.setActive(null);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), any())).thenReturn(true); // B1b: the lock is only revealed to someone who knows the password
        assertThatThrownBy(() -> service.login(new LoginRequest("tien@example.com", "secret12"), null, null))
            .satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
    }

    @Test
    void login_staffInactive_isAuditedAsLocked() {
        User u = Fx.user(9L, "boss", "boss@example.com", "ADMIN");
        u.setActive(false);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        when(encoder.matches(anyString(), any())).thenReturn(true); // B1b: the lock is only revealed to someone who knows the password
        assertThatThrownBy(() -> service.login(new LoginRequest("boss@example.com", "x"), null, null))
            .isInstanceOf(ResponseStatusException.class);
        verify(audit).recordBy(eq("boss"), eq("đăng nhập thất bại"), eq("tài khoản bị khóa"));
    }

    @Test
    void login_deletedAccountShell_cannotSignIn() {
        User u = customer();
        u.setActive(false);
        u.setAccountStatus("DELETED");
        when(users.findByEmail(anyString())).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.login(new LoginRequest("tien@example.com", "secret12"), null, null))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void login_nullRequest_is400() {
        assertThatThrownBy(() -> service.login(null, null, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void login_blankIdentifierOrPassword_is400(String blank) {
        assertThatThrownBy(() -> service.login(new LoginRequest(blank, "x"), null, null))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.login(new LoginRequest("tien", blank), null, null))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    /**
     * Account enumeration: an unknown login returns 404 "user not found" while a wrong password returns 401, so anybody can probe
     * which usernames/emails exist. Expected: the same 401 for both. Actual: 404.
     * AuthenticationServiceImpl.java:~220 (findUser throws NOT_FOUND and login does not translate it).
     */
    @Test
    void regression_login_unknownUserMustBeIndistinguishableFromWrongPassword() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", "whatever1"), null, null))
            .satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
    }

    @Test
    void login_unknownUserAndWrongPasswordGiveTheSameStatusMessageAndBodyThroughTheHandler() {
        User u = customer();
        when(users.findByEmail("tien@example.com")).thenReturn(Optional.of(u));
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        var handler = new com.ebike.common.exception.ApiExceptionHandler();

        var wrong = handlerAnswer(handler, "tien@example.com");
        var unknown = handlerAnswer(handler, "ghost@example.com");

        assertThat(unknown.getStatusCode()).isEqualTo(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getBody().message()).isEqualTo(wrong.getBody().message());
        assertThat(unknown.getBody().error()).isEqualTo(wrong.getBody().error());
        assertThat(unknown.getBody().status()).isEqualTo(wrong.getBody().status());
    }

    private org.springframework.http.ResponseEntity<com.ebike.common.exception.ApiExceptionHandler.ApiErrorResponse> handlerAnswer(
        com.ebike.common.exception.ApiExceptionHandler handler, String identifier) {
        try {
            service.login(new LoginRequest(identifier, "whatever1"), null, null);
            throw new AssertionError("login should have been refused");
        } catch (ResponseStatusException e) {
            return handler.handleResponseStatusException(e);
        }
    }

    /**
     * register() hashes password.trim() but login() compares the raw password, so a person who typed " secret12 " in the sign-up form
     * can never log in with the same text. Expected: login(" secret12 ") works (or register must not trim). Actual: 401.
     */
    @Test
    void regression_registerTrimsPasswordButLoginDoesNot() {
        service.register(reg("tien", "a@b.com", " secret12 ", null, null));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User stored = saved.getValue();
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(stored));
        when(encoder.matches(anyString(), anyString())).thenAnswer(inv -> ("ENC(" + inv.getArgument(0) + ")").equals(inv.getArgument(1)));
        assertThat(service.login(new LoginRequest("a@b.com", " secret12 "), null, null)).isNotNull();
    }

    // ------------------------------------------------------------------ profile

    @Test
    void getProfile_returnsRolesAndPermissions() {
        User u = customer();
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        when(permissions.resolvePermissionCodes(u)).thenReturn(java.util.Set.of("profile:view"));
        var p = service.getProfile("tien");
        assertThat(p.permissions()).containsExactly("profile:view");
        assertThat(p.roles()).containsExactly("CUSTOMER");
        assertThat(p.email()).isEqualTo("tien@example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void getProfile_blank_is400(String blank) {
        assertThatThrownBy(() -> service.getProfile(blank)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void getProfile_unknown_is404() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProfile("ghost")).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void updateProfile_changesNamesAndEmail_normalised() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        when(encoder.matches("oldpass1", u.getPasswordHash())).thenReturn(true);
        var out = service.updateProfile("tien", new UpdateProfileRequest(" Anh ", null, "  NEW@Example.com ", "oldpass1"));
        assertThat(out.firstName()).isEqualTo("Anh");
        assertThat(out.lastName()).as("a name that was not sent stays").isEqualTo("Tien");
        assertThat(out.email()).isEqualTo("new@example.com");
    }

    @Test
    void updateProfile_emailTakenByAnotherAccount_is409_andEmailUnchanged() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        when(users.existsByEmail("other@example.com")).thenReturn(true);
        when(encoder.matches("oldpass1", u.getPasswordHash())).thenReturn(true);
        assertThatThrownBy(() -> service.updateProfile("tien", new UpdateProfileRequest("A", "B", "Other@Example.com", "oldpass1")))
            .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        assertThat(u.getEmail()).isEqualTo("tien@example.com");
        verify(users, never()).save(any());
    }

    @Test
    void updateProfile_sameEmailDifferentCase_isNotAConflict() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        service.updateProfile("tien", new UpdateProfileRequest("A", "B", "TIEN@example.com"));
        verify(users, never()).existsByEmail(anyString());
        assertThat(u.getEmail()).isEqualTo("tien@example.com");
    }

    @Test
    void updateProfile_blankEmail_keepsCurrentEmail() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        service.updateProfile("tien", new UpdateProfileRequest("A", "B", "  "));
        assertThat(u.getEmail()).isEqualTo("tien@example.com");
    }

    @Test
    void updateProfile_nullRequestAndBlankCaller_are400() {
        assertThatThrownBy(() -> service.updateProfile("tien", null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateProfile(" ", new UpdateProfileRequest(null, null, null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    /** Register validates the email format; the profile form must apply the same rule (the welcome/OTP emails go there). */
    @Test
    void regression_updateProfile_acceptsMalformedEmail() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.updateProfile("tien", new UpdateProfileRequest("A", "B", "not-an-email")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(u.getEmail()).isEqualTo("tien@example.com");
    }

    @Test
    void regression_updateProfile_firstNameOf300CharactersMustBeRefusedWith400() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.updateProfile("tien", new UpdateProfileRequest("A".repeat(300), "B", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    /** Live finding: the profile form accepts blank first and last name and stores null (the signup form marks both required). */
    @Test
    void regression_updateProfile_blankNamesAreSilentlyStoredAsNull() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.updateProfile("tien", new UpdateProfileRequest("  ", "", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateProfile_emailChangeWithoutCurrentPassword_is400_andNothingChanges() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.updateProfile("tien", new UpdateProfileRequest("A", "B", "new@example.com")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateProfile("tien", new UpdateProfileRequest("A", "B", "new@example.com", "  ")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(u.getEmail()).isEqualTo("tien@example.com");
        verify(users, never()).save(any());
    }

    @Test
    void updateProfile_emailChangeWithWrongCurrentPassword_is400_andNothingChanges() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        when(encoder.matches("wrong-pass", u.getPasswordHash())).thenReturn(false);
        assertThatThrownBy(() -> service.updateProfile("tien", new UpdateProfileRequest("A", "B", "new@example.com", "wrong-pass")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(u.getEmail()).isEqualTo("tien@example.com");
        verify(users, never()).save(any());
    }

    @Test
    void updateProfile_emailChangeWithRightPassword_isSaved() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        when(encoder.matches("oldpass1", u.getPasswordHash())).thenReturn(true);
        var out = service.updateProfile("tien", new UpdateProfileRequest("A", "B", "New@Example.com", "oldpass1"));
        assertThat(out.email()).isEqualTo("new@example.com");
    }

    @Test
    void updateProfile_unchangedEmailNeedsNoPassword() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        var out = service.updateProfile("tien", new UpdateProfileRequest("A", "B", "TIEN@example.com"));
        assertThat(out.firstName()).isEqualTo("A");
    }

    /** Only the fields that are sent are checked: an old account with no last name can still be saved with just another first name. */
    @Test
    void updateProfile_legacyAccountWithNullLastName_canStillChangeFirstName() {
        User u = customer();
        u.setLastName(null);
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        var out = service.updateProfile("tien", new UpdateProfileRequest("Anh", null, null));
        assertThat(out.firstName()).isEqualTo("Anh");
        assertThat(out.lastName()).isNull();
    }

    @Test
    void register_passwordIsHashedExactlyAsTyped_neverTrimmed() {
        service.register(reg("tien", "a@b.com", " secret12 ", null, null));
        verify(encoder).encode(" secret12 ");
    }

    // ------------------------------------------------------------------ change password

    private void givenSignedInCustomer() {
        User u = customer();
        when(users.findByEmail("tien")).thenReturn(Optional.empty());
        when(users.findByUsername("tien")).thenReturn(Optional.of(u));
        when(encoder.matches("oldpass1", u.getPasswordHash())).thenReturn(true);
    }

    @Test
    void changePassword_success_hashesNewPassword() {
        givenSignedInCustomer();
        service.changePassword("tien", new ChangePasswordRequest("oldpass1", "newpass1", "newpass1"));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("ENC(newpass1)");
    }

    @Test
    void changePassword_wrongCurrent_is400_andNothingSaved() {
        givenSignedInCustomer();
        assertThatThrownBy(() -> service.changePassword("tien", new ChangePasswordRequest("guess", "newpass1", "newpass1")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    @Test
    void changePassword_confirmMismatch_is400() {
        givenSignedInCustomer();
        assertThatThrownBy(() -> service.changePassword("tien", new ChangePasswordRequest("oldpass1", "newpass1", "newpass2")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    @Test
    void changePassword_newEqualsCurrent_is400() {
        givenSignedInCustomer();
        assertThatThrownBy(() -> service.changePassword("tien", new ChangePasswordRequest("oldpass1", "oldpass1", "oldpass1")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void changePassword_sevenCharacters_is400_eightAccepted() {
        givenSignedInCustomer();
        assertThatThrownBy(() -> service.changePassword("tien", new ChangePasswordRequest("oldpass1", "1234567", "1234567")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        service.changePassword("tien", new ChangePasswordRequest("oldpass1", "12345678", "12345678"));
        verify(users).save(any(User.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void changePassword_anyBlankField_is400(String blank) {
        givenSignedInCustomer();
        assertThatThrownBy(() -> service.changePassword("tien", new ChangePasswordRequest(blank, "newpass1", "newpass1")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.changePassword("tien", new ChangePasswordRequest("oldpass1", blank, "newpass1")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.changePassword("tien", new ChangePasswordRequest("oldpass1", "newpass1", blank)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void changePassword_nullRequestOrCaller_is400() {
        assertThatThrownBy(() -> service.changePassword("tien", null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.changePassword(null, new ChangePasswordRequest("a", "b", "b")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void changePassword_unknownUser_is404() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changePassword("ghost", new ChangePasswordRequest("oldpass1", "newpass1", "newpass1")))
            .satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }
}
