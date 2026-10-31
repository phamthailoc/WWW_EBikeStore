package com.ebike.userModule.service;

import java.util.List;
import org.springframework.security.core.Authentication;

public interface UserProfileService {

    UserBasicResponse getUser(Long userId, Authentication authentication);

    List<UserAddressResponse> getAddresses(Long userId, Authentication authentication);

    UserAddressResponse createAddress(Long userId, UserAddressRequest request, Authentication authentication);

    UserAddressResponse updateAddress(Long userId, Long addressId, UserAddressRequest request, Authentication authentication);

    void deleteAddress(Long userId, Long addressId, Authentication authentication);

    UserPreferenceResponse getPreferences(Long userId, Authentication authentication);

    UserPreferenceResponse upsertPreferences(Long userId, UserPreferenceRequest request, Authentication authentication);

    record UserAddressRequest(
        String addressType,
        String street,
        String city,
        String postalCode,
        String country,
        Boolean isDefault
    ) {
    }

    record UserPreferenceRequest(String theme, String language, Boolean notificationsEnabled) {
    }

    record UserBasicResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean active,
        Boolean verified
    ) {
    }

    record UserAddressResponse(
        Long id,
        Long userId,
        String addressType,
        String street,
        String city,
        String postalCode,
        String country,
        Boolean isDefault
    ) {
    }

    record UserPreferenceResponse(Long userId, String theme, String language, Boolean notificationsEnabled) {
    }
}
