package com.ebike.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.user.UserAddress;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserAddressRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.user.UserProfileService.UserAddressRequest;
import com.ebike.service.user.impl.UserProfileServiceImpl;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/** V4: the saved address book is what checkout pre-fills, so it must hold complete addresses and stay small. */
@ExtendWith(MockitoExtension.class)
class UserAddressBookTest {
    @Mock UserRepository users;
    @Mock UserAddressRepository addresses;
    @Mock UserPreferenceRepository preferences;
    @Mock Authentication auth;
    UserProfileServiceImpl service;
    User user;

    @BeforeEach void setup() {
        service = new UserProfileServiceImpl(users, addresses, preferences);
        user = new User();
        user.setId(5L);
        when(auth.getName()).thenReturn("an");
        when(auth.getAuthorities()).thenReturn(Collections.emptyList());
        when(users.findByUsername("an")).thenReturn(Optional.of(user));
        when(users.findById(5L)).thenReturn(Optional.of(user));
    }

    private UserAddressRequest request(String street, String city) {
        return new UserAddressRequest("SHIPPING", street, city, null, null, false);
    }

    @Test void aCompleteAddressIsSavedWithVietnamAsTheDefaultCountry() {
        when(addresses.findByUserId(5L)).thenReturn(List.of());
        when(addresses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = service.createAddress(5L, request("123 Nguyễn Trãi, Phường Bến Thành", "Hồ Chí Minh"), auth);
        assertEquals("Hồ Chí Minh", saved.city());
        assertEquals("Việt Nam", saved.country());
    }

    @Test void aShortStreetOrMissingProvinceIsRejected() {
        assertThrows(ResponseStatusException.class, () -> service.createAddress(5L, request("Số 1", "Hà Nội"), auth));
        assertThrows(ResponseStatusException.class, () -> service.createAddress(5L, request("123 Nguyễn Trãi, Phường Bến Thành", " "), auth));
        assertThrows(ResponseStatusException.class, () -> service.createAddress(5L, request(null, "Hà Nội"), auth));
        verify(addresses, never()).save(any());
    }

    @Test void editsAreValidatedToo() {
        UserAddress existing = new UserAddress();
        when(addresses.findByIdAndUserId(9L, 5L)).thenReturn(Optional.of(existing));
        assertThrows(ResponseStatusException.class, () -> service.updateAddress(5L, 9L, request("Số 1", "Hà Nội"), auth));
        verify(addresses, never()).save(any());
    }

    @Test void thereIsALimitOfTenAddresses() {
        when(addresses.findByUserId(5L)).thenReturn(Collections.nCopies(10, new UserAddress()));
        assertThrows(ResponseStatusException.class,
            () -> service.createAddress(5L, request("123 Nguyễn Trãi, Phường Bến Thành", "Hồ Chí Minh"), auth));
        verify(addresses, never()).save(any());
    }
}
