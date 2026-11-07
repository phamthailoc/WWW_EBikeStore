package com.ebike.controller.user;

import com.ebike.service.user.UserProfileService;
import com.ebike.service.user.UserProfileService.UserAddressRequest;
import com.ebike.service.user.UserProfileService.UserAddressResponse;
import com.ebike.service.user.UserProfileService.UserBasicResponse;
import com.ebike.service.user.UserProfileService.UserPreferenceRequest;
import com.ebike.service.user.UserProfileService.UserPreferenceResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/{userId}")
    public UserBasicResponse getUser(@PathVariable Long userId, Authentication authentication) {
        return userProfileService.getUser(userId, authentication);
    }

    @GetMapping("/{userId}/addresses")
    public List<UserAddressResponse> getAddresses(@PathVariable Long userId, Authentication authentication) {
        return userProfileService.getAddresses(userId, authentication);
    }

    @PostMapping("/{userId}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAddressResponse createAddress(
        @PathVariable Long userId,
        @RequestBody UserAddressRequest request,
        Authentication authentication
    ) {
        return userProfileService.createAddress(userId, request, authentication);
    }

  @PutMapping("/{userId}/addresses/{addressId}")
  public UserAddressResponse updateAddress(
      @PathVariable Long userId,
      @PathVariable Long addressId,
      @RequestBody UserAddressRequest request,
      Authentication authentication
  ) {
    return userProfileService.updateAddress(userId, addressId, request, authentication);
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/{userId}/addresses/{addressId}")
  public void deleteAddress(
      @PathVariable Long userId,
      @PathVariable Long addressId,
      Authentication authentication
  ) {
    userProfileService.deleteAddress(userId, addressId, authentication);
  }

    @GetMapping("/{userId}/preferences")
    public UserPreferenceResponse getPreferences(@PathVariable Long userId, Authentication authentication) {
        return userProfileService.getPreferences(userId, authentication);
    }

    @PutMapping("/{userId}/preferences")
    public UserPreferenceResponse upsertPreferences(
        @PathVariable Long userId,
        @RequestBody UserPreferenceRequest request,
        Authentication authentication
    ) {
        return userProfileService.upsertPreferences(userId, request, authentication);
    }
}
