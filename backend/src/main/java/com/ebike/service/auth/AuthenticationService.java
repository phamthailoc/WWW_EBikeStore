package com.ebike.service.auth;

import com.ebike.dto.auth.response.AuthResponse;
import com.ebike.dto.auth.response.EnhancedAuthResponse;
import com.ebike.dto.auth.request.ChangePasswordRequest;
import com.ebike.dto.auth.request.LoginRequest;
import com.ebike.dto.auth.request.RegisterRequest;
import com.ebike.dto.auth.request.UpdateProfileRequest;
import com.ebike.dto.auth.response.RoleSpecificLoginResponse;
import com.ebike.dto.auth.response.UserProfileResponse;

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
