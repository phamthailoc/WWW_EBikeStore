package com.ebike.authModule.controller;

import com.ebike.authModule.dto.response.AuthResponse;
import com.ebike.authModule.dto.response.EnhancedAuthResponse;
import com.ebike.authModule.dto.request.ChangePasswordRequest;
import com.ebike.authModule.dto.request.LoginRequest;
import com.ebike.authModule.dto.request.RegisterRequest;
import com.ebike.authModule.dto.request.UpdateProfileRequest;
import com.ebike.authModule.dto.response.RoleSpecificLoginResponse;
import com.ebike.authModule.dto.response.UserProfileResponse;
import com.ebike.authModule.service.AuthenticationService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private static final String ACCESS_TOKEN_COOKIE_NAME = "ebike_access_token";
    private static final String ROLE_CUSTOMER = "CUSTOMER";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_ADMIN = "ADMIN";

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    @Value("${app.auth.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${app.auth.cookie.same-site:Lax}")
    private String sameSite;

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
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

    @PostMapping("/login/enhanced")
    public ResponseEntity<EnhancedAuthResponse> enhancedLogin(
        @RequestBody LoginRequest request,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse
    ) {
        EnhancedAuthResponse authResponse = authenticationService.enhancedLogin(
            request,
            httpServletRequest.getRemoteAddr(),
            httpServletRequest.getHeader("User-Agent")
        );
        attachAuthCookie(httpServletResponse, authResponse.token());
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/login/customer")
    public ResponseEntity<RoleSpecificLoginResponse> customerLogin(
        @RequestBody LoginRequest request,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse
    ) {
        RoleSpecificLoginResponse response = authenticationService.roleSpecificLogin(
            request,
            ROLE_CUSTOMER,
            httpServletRequest.getRemoteAddr(),
            httpServletRequest.getHeader("User-Agent")
        );
        attachAuthCookie(httpServletResponse, response.authResponse().token());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/manager")
    public ResponseEntity<RoleSpecificLoginResponse> managerLogin(
        @RequestBody LoginRequest request,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse
    ) {
        RoleSpecificLoginResponse response = authenticationService.roleSpecificLogin(
            request,
            ROLE_MANAGER,
            httpServletRequest.getRemoteAddr(),
            httpServletRequest.getHeader("User-Agent")
        );
        attachAuthCookie(httpServletResponse, response.authResponse().token());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/admin")
    public ResponseEntity<RoleSpecificLoginResponse> adminLogin(
        @RequestBody LoginRequest request,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse
    ) {
        RoleSpecificLoginResponse response = authenticationService.roleSpecificLogin(
            request,
            ROLE_ADMIN,
            httpServletRequest.getRemoteAddr(),
            httpServletRequest.getHeader("User-Agent")
        );
        attachAuthCookie(httpServletResponse, response.authResponse().token());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse httpServletResponse) {
        clearAuthCookie(httpServletResponse);
    }

    @GetMapping("/session")
    public ResponseEntity<UserProfileResponse> getSession(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(authenticationService.getProfile(authentication.getName()));
    }

    @GetMapping("/profile")
    public UserProfileResponse getProfile(
        Authentication authentication,
        @RequestParam(required = false) String usernameOrEmail
    ) {
        String resolvedIdentity = usernameOrEmail;
        if ((resolvedIdentity == null || resolvedIdentity.isBlank()) && authentication != null) {
            resolvedIdentity = authentication.getName();
        }
        return authenticationService.getProfile(resolvedIdentity);
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
        try {
            logger.debug("changePassword called by {}", authentication.getName());
            authenticationService.changePassword(authentication.getName(), request);
        } catch (Exception e) {
            logger.error("changePassword failed for {}", authentication != null ? authentication.getName() : "unknown", e);
            throw e;
        }
    }

    @GetMapping("/verify-token")
    public boolean verifyToken(
        @RequestHeader(value = "Authorization", required = false) String token,
        HttpServletRequest request
    ) {
        String jwtToken = extractToken(token, request);
        if (jwtToken == null) {
            return false;
        }
        return authenticationService.validateToken(jwtToken);
    }

    @GetMapping("/user-from-token")
    public String getUserFromToken(
        @RequestHeader(value = "Authorization", required = false) String token,
        HttpServletRequest request
    ) {
        String jwtToken = extractToken(token, request);
        if (jwtToken == null) {
            return null;
        }
        if (authenticationService.validateToken(jwtToken)) {
            return authenticationService.extractUsernameFromToken(jwtToken);
        }
        return null;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring(7);
    }

    private String extractToken(String authorizationHeader, HttpServletRequest request) {
        String bearerToken = extractBearerToken(authorizationHeader);
        if (bearerToken != null) {
            return bearerToken;
        }
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
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
