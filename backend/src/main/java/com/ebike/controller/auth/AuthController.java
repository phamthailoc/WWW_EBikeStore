package com.ebike.controller.auth;

import com.ebike.dto.auth.response.AuthResponse;
import com.ebike.dto.auth.request.ChangePasswordRequest;
import com.ebike.dto.auth.request.LoginRequest;
import com.ebike.dto.auth.request.RegisterRequest;
import com.ebike.dto.auth.request.UpdateProfileRequest;
import com.ebike.dto.auth.response.UserProfileResponse;
import com.ebike.service.auth.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "ebike_access_token";

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    @Value("${app.auth.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${app.auth.cookie.same-site:Lax}")
    private String sameSite;

    private final AuthenticationService authenticationService;

    private final com.ebike.service.user.OwnAccountService ownAccountService;

    public AuthController(AuthenticationService authenticationService, com.ebike.service.user.OwnAccountService ownAccountService) {
        this.authenticationService = authenticationService;
        this.ownAccountService = ownAccountService;
    }

    /** A customer erases their own account and is signed out. */
    @org.springframework.web.bind.annotation.DeleteMapping("/account")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteOwnAccount(
        @RequestBody com.ebike.dto.auth.request.DeleteOwnAccountRequest request,
        org.springframework.security.core.Authentication authentication,
        HttpServletResponse httpServletResponse
    ) {
        ownAccountService.deleteOwnAccount(authentication, request);
        clearAuthCookie(httpServletResponse);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authenticationService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @RequestBody LoginRequest request,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse
    ) {
        AuthResponse authResponse = authenticationService.login(
            request,
            httpServletRequest.getRemoteAddr(),
            httpServletRequest.getHeader("User-Agent")
        );
        attachAuthCookie(httpServletResponse, authResponse.token());
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse httpServletResponse) {
        clearAuthCookie(httpServletResponse);
    }

    @GetMapping("/profile")
    public UserProfileResponse getProfile(Authentication authentication) {
        // Always the signed-in account: a caller must not be able to read another account's profile by name or email.
        return authenticationService.getProfile(authentication.getName());
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(
        Authentication authentication,
        @RequestBody UpdateProfileRequest request
    ) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return authenticationService.updateProfile(authentication.getName(), request);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
        Authentication authentication,
        @RequestBody ChangePasswordRequest request
    ) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        authenticationService.changePassword(authentication.getName(), request);
    }

    private void attachAuthCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, token)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite(sameSite)
            .path("/")
            .maxAge(Duration.ofMillis(jwtExpirationMs))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite(sameSite)
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
