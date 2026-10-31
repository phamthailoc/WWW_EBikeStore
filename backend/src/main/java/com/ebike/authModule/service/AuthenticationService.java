package com.ebike.authModule.service;

import com.ebike.authModule.dto.response.AuthResponse;
import com.ebike.authModule.dto.response.EnhancedAuthResponse;
import com.ebike.authModule.dto.request.ChangePasswordRequest;
import com.ebike.authModule.dto.request.LoginRequest;
import com.ebike.authModule.dto.request.RegisterRequest;
import com.ebike.authModule.dto.request.UpdateProfileRequest;
import com.ebike.authModule.dto.response.RoleSpecificLoginResponse;
import com.ebike.authModule.dto.response.UserProfileResponse;

public interface AuthenticationService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request, String ipAddress, String userAgent);

    EnhancedAuthResponse enhancedLogin(LoginRequest request, String ipAddress, String userAgent);

    RoleSpecificLoginResponse roleSpecificLogin(
        LoginRequest request,
        String requiredRole,
        String ipAddress,
        String userAgent
    );

    UserProfileResponse getProfile(String usernameOrEmail);

    UserProfileResponse updateProfile(String usernameOrEmail, UpdateProfileRequest request);

    void changePassword(String usernameOrEmail, ChangePasswordRequest request);

    boolean validateToken(String token);

    String extractUsernameFromToken(String token);
}
