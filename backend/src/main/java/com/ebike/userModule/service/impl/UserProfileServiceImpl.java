package com.ebike.userModule.service.impl;

import com.ebike.authModule.entity.User;
import com.ebike.authModule.repository.UserRepository;
import com.ebike.shared.constants.PermissionConstants;
import com.ebike.userModule.entity.AddressType;
import com.ebike.userModule.entity.UserAddress;
import com.ebike.userModule.entity.UserPreference;
import com.ebike.userModule.repository.UserAddressRepository;
import com.ebike.userModule.repository.UserPreferenceRepository;
import com.ebike.userModule.service.UserProfileService;
import com.ebike.userModule.service.UserProfileService.UserAddressRequest;
import com.ebike.userModule.service.UserProfileService.UserAddressResponse;
import com.ebike.userModule.service.UserProfileService.UserBasicResponse;
import com.ebike.userModule.service.UserProfileService.UserPreferenceRequest;
import com.ebike.userModule.service.UserProfileService.UserPreferenceResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    public UserProfileServiceImpl(
        UserRepository userRepository,
        UserAddressRepository userAddressRepository,
        UserPreferenceRepository userPreferenceRepository
    ) {
        this.userRepository = userRepository;
        this.userAddressRepository = userAddressRepository;
        this.userPreferenceRepository = userPreferenceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserBasicResponse getUser(Long userId, Authentication authentication) {
        assertCanAccessUser(userId, authentication);
        return toUserBasicResponse(getUserOrThrow(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAddressResponse> getAddresses(Long userId, Authentication authentication) {
        assertCanAccessUser(userId, authentication);
        validateUser(userId);
        return userAddressRepository.findByUserId(userId).stream()
            .sorted(Comparator.comparing(UserAddress::getId))
            .map(this::toAddressResponse)
            .toList();
    }

    @Override
    @Transactional
    public UserAddressResponse createAddress(Long userId, UserAddressRequest request, Authentication authentication) {
        assertCanAccessUser(userId, authentication);
        User user = getUserOrThrow(userId);

        UserAddress address = new UserAddress();
        address.setUser(user);
        address.setAddressType(parseAddressType(request == null ? null : request.addressType()));
        address.setStreet(request == null ? null : request.street());
        address.setCity(request == null ? null : request.city());
        address.setPostalCode(request == null ? null : request.postalCode());
        address.setCountry(request == null ? null : request.country());
        address.setDefaultAddress(request != null && Boolean.TRUE.equals(request.isDefault()));

        if (Boolean.TRUE.equals(request != null ? request.isDefault() : null)) {
            unsetDefaultAddresses(userId, null);
        }

        return toAddressResponse(userAddressRepository.save(address));
    }

    @Override
    @Transactional
    public UserAddressResponse updateAddress(Long userId, Long addressId, UserAddressRequest request, Authentication authentication) {
        assertCanAccessUser(userId, authentication);
        validateUser(userId);
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        address.setAddressType(parseAddressType(request == null ? null : request.addressType()));
        address.setStreet(request == null ? null : request.street());
        address.setCity(request == null ? null : request.city());
        address.setPostalCode(request == null ? null : request.postalCode());
        address.setCountry(request == null ? null : request.country());
        address.setDefaultAddress(request != null && Boolean.TRUE.equals(request.isDefault()));

        if (Boolean.TRUE.equals(request != null ? request.isDefault() : null)) {
            unsetDefaultAddresses(userId, address.getId());
        }

        return toAddressResponse(userAddressRepository.save(address));
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId, Authentication authentication) {
        assertCanAccessUser(userId, authentication);
        validateUser(userId);
        userAddressRepository.findByIdAndUserId(addressId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        userAddressRepository.deleteByIdAndUserId(addressId, userId);
    }

    private void unsetDefaultAddresses(Long userId, Long keepAddressId) {
        List<UserAddress> existingDefaults = userAddressRepository.findByUserIdAndDefaultAddressTrue(userId);
        for (UserAddress existing : existingDefaults) {
            if (keepAddressId == null || !existing.getId().equals(keepAddressId)) {
                existing.setDefaultAddress(false);
            }
        }
        if (!existingDefaults.isEmpty()) {
            userAddressRepository.saveAll(existingDefaults);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserPreferenceResponse getPreferences(Long userId, Authentication authentication) {
        assertCanAccessUser(userId, authentication);
        validateUser(userId);
        UserPreference preference = userPreferenceRepository.findByUserId(userId).orElse(null);
        return preference == null
            ? new UserPreferenceResponse(userId, "light", "en", true)
            : toPreferenceResponse(preference);
    }

    @Override
    @Transactional
    public UserPreferenceResponse upsertPreferences(Long userId, UserPreferenceRequest request, Authentication authentication) {
        assertCanAccessUser(userId, authentication);
        User user = getUserOrThrow(userId);

        UserPreference preference = userPreferenceRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserPreference created = new UserPreference();
                created.setUser(user);
                return created;
            });

        if (request != null && request.theme() != null && !request.theme().isBlank()) {
            preference.setTheme(request.theme().trim());
        }
        if (request != null && request.language() != null && !request.language().isBlank()) {
            preference.setLanguage(request.language().trim());
        }
        if (request != null && request.notificationsEnabled() != null) {
            preference.setNotificationsEnabled(request.notificationsEnabled());
        }

        return toPreferenceResponse(userPreferenceRepository.save(preference));
    }

    private User getUserOrThrow(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void validateUser(Long userId) {
        getUserOrThrow(userId);
    }

    private void assertCanAccessUser(Long userId, Authentication authentication) {
        if (isBackOffice(authentication)) {
            return;
        }
        User currentUser = getAuthenticatedUser(authentication);
        if (!currentUser.getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own profile data");
        }
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
            .or(() -> userRepository.findByEmail(authentication.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private boolean isBackOffice(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority ->
                PermissionConstants.UserManagement.USER_VIEW.equals(authority.getAuthority())
                    || PermissionConstants.UserManagement.USER_MANAGE.equals(authority.getAuthority())
            );
    }

    private AddressType parseAddressType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AddressType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid addressType. Use BILLING or SHIPPING");
        }
    }

    private UserBasicResponse toUserBasicResponse(User user) {
        return new UserBasicResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getActive(),
            user.getVerified()
        );
    }

    private UserAddressResponse toAddressResponse(UserAddress address) {
        return new UserAddressResponse(
            address.getId(),
            address.getUser().getId(),
            address.getAddressType() == null ? null : address.getAddressType().name(),
            address.getStreet(),
            address.getCity(),
            address.getPostalCode(),
            address.getCountry(),
            address.getDefaultAddress()
        );
    }

    private UserPreferenceResponse toPreferenceResponse(UserPreference preference) {
        return new UserPreferenceResponse(
            preference.getUser().getId(),
            preference.getTheme(),
            preference.getLanguage(),
            preference.getNotificationsEnabled()
        );
    }

}
