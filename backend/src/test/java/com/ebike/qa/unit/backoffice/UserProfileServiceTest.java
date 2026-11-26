package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.user.UserAddress;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserAddressRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.user.UserProfileService.UserAddressRequest;
import com.ebike.service.user.UserProfileService.UserPreferenceRequest;
import com.ebike.service.user.impl.UserProfileServiceImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class UserProfileServiceTest {

    UserRepository users = mock(UserRepository.class);
    UserAddressRepository addresses = mock(UserAddressRepository.class);
    UserPreferenceRepository prefs = mock(UserPreferenceRepository.class);
    UserProfileServiceImpl service;
    User me = Fx.user(5L, "tien", "tien@example.com", "CUSTOMER");
    User other = Fx.user(6L, "bob", "bob@example.com", "CUSTOMER");
    User admin = Fx.user(1L, "root", "root@example.com", "ADMIN");
    Authentication meAuth = Fx.auth("tien", "profile:view", "profile:update");

    @BeforeEach
    void setUp() {
        service = new UserProfileServiceImpl(users, addresses, prefs);
        for (User u : List.of(me, other, admin)) {
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
            when(users.findByUsername(u.getUsername())).thenReturn(Optional.of(u));
        }
        when(addresses.save(any(UserAddress.class))).thenAnswer(i -> {
            UserAddress a = i.getArgument(0);
            if (a.getId() == null) {
                ReflectionTestUtils.setField(a, "id", 100L);
            }
            return a;
        });
        when(prefs.save(any(UserPreference.class))).thenAnswer(i -> i.getArgument(0));
        when(addresses.findByUserId(any())).thenReturn(new ArrayList<>());
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(status.value());
    }

    private static UserAddressRequest addr(String street, String city, Boolean def) {
        return new UserAddressRequest("SHIPPING", street, city, "70000", null, def);
    }

    private UserAddress saved(long id, User owner, boolean def) {
        UserAddress a = new UserAddress();
        ReflectionTestUtils.setField(a, "id", id);
        a.setUser(owner);
        a.setStreet("123 Nguyen Trai, P1");
        a.setCity("HCM");
        a.setDefaultAddress(def);
        return a;
    }

    // ------------------------------------------------------------------ ownership

    @Test
    void customer_canReadOwnProfile_notSomeoneElses() {
        assertThat(service.getUser(5L, meAuth).username()).isEqualTo("tien");
        assertThatThrownBy(() -> service.getUser(6L, meAuth)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
    }

    @Test
    void customer_cannotTouchAnotherUsersAddressesOrPreferences() {
        assertThatThrownBy(() -> service.getAddresses(6L, meAuth)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.createAddress(6L, addr("123 Nguyen Trai, P1", "HCM", false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.updateAddress(6L, 1L, addr("123 Nguyen Trai, P1", "HCM", false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.deleteAddress(6L, 1L, meAuth)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.getPreferences(6L, meAuth)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.upsertPreferences(6L, new UserPreferenceRequest("dark", true), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
        verify(addresses, never()).save(any());
        verify(addresses, never()).deleteByIdAndUserId(any(), any());
        verify(prefs, never()).save(any());
    }

    @Test
    void anonymousOrUnknownPrincipal_is401() {
        assertThatThrownBy(() -> service.getUser(5L, null)).satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.getUser(5L, Fx.auth(" "))).satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.getUser(5L, Fx.auth("ghost"))).satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
    }

    @Test
    void principalMayBeIdentifiedByEmail() {
        when(users.findByUsername("tien@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("tien@example.com")).thenReturn(Optional.of(me));
        assertThat(service.getUser(5L, Fx.auth("tien@example.com")).id()).isEqualTo(5L);
    }

    @Test
    void backOfficeWithUserView_canReadAnyCustomer() {
        Authentication mgr = Fx.auth("mgr", PermissionConstants.UserManagement.USER_VIEW);
        assertThat(service.getUser(6L, mgr).username()).isEqualTo("bob");
    }

    /**
     * user:view is described as "View customer account data" (setup_ebike_database.sql). A manager holding it can read the profile of an
     * ADMIN account through GET /api/v1/users/{id} (UserProfileServiceImpl.java:isBackOffice + getUser has no role check).
     * Expected: 403/404 for an admin's record. Actual: returns username and email of the admin.
     */
    @Test
    @Tag("bug")
    void bug_managerWithUserViewCanReadAnAdminAccount() {
        Authentication mgr = Fx.auth("mgr", PermissionConstants.UserManagement.USER_VIEW);
        assertThatThrownBy(() -> service.getUser(1L, mgr)).isInstanceOf(ResponseStatusException.class);
    }

    /** A read-only permission (user:view) must not allow writing another person's address book. */
    @Test
    @Tag("bug")
    void bug_userViewAloneAllowsCreatingAddressForAnotherUser() {
        Authentication viewer = Fx.auth("mgr", PermissionConstants.UserManagement.USER_VIEW);
        assertThatThrownBy(() -> service.createAddress(6L, addr("123 Nguyen Trai, P1", "HCM", false), viewer))
            .satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
    }

    @Test
    @Tag("bug")
    void bug_userViewAloneAllowsDeletingAnotherUsersAddress() {
        Authentication viewer = Fx.auth("mgr", PermissionConstants.UserManagement.USER_VIEW);
        when(addresses.findByIdAndUserId(9L, 6L)).thenReturn(Optional.of(saved(9L, other, false)));
        assertThatThrownBy(() -> service.deleteAddress(6L, 9L, viewer)).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
    }

    @Test
    void unknownUserId_is404_nullIsBadRequest() {
        Authentication mgr = Fx.auth("mgr", PermissionConstants.UserManagement.USER_MANAGE);
        assertThatThrownBy(() -> service.getUser(999L, mgr)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.getUser(null, mgr)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    // ------------------------------------------------------------------ addresses

    @Test
    void createAddress_trimsFields_defaultsCountry() {
        var res = service.createAddress(5L, addr("  123 Nguyen Trai, P1  ", " Ho Chi Minh ", false), meAuth);
        assertThat(res.street()).isEqualTo("123 Nguyen Trai, P1");
        assertThat(res.city()).isEqualTo("Ho Chi Minh");
        assertThat(res.country()).isEqualTo("Việt Nam");
        assertThat(res.addressType()).isEqualTo("SHIPPING");
        assertThat(res.userId()).isEqualTo(5L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void createAddress_streetBelowTenCharacters_isRefused(int length) {
        assertThatThrownBy(() -> service.createAddress(5L, addr("x".repeat(length), "HCM", false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(addresses, never()).save(any());
    }

    @Test
    void createAddress_streetBoundaries_10ok_255ok_256refused_whitespaceDoesNotCount() {
        service.createAddress(5L, addr("x".repeat(10), "HCM", false), meAuth);
        service.createAddress(5L, addr("x".repeat(255), "HCM", false), meAuth);
        assertThatThrownBy(() -> service.createAddress(5L, addr("x".repeat(256), "HCM", false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createAddress(5L, addr("   123 a b   ", "HCM", false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void createAddress_cityRequired_max100() {
        assertThatThrownBy(() -> service.createAddress(5L, addr("123 Nguyen Trai, P1", null, false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createAddress(5L, addr("123 Nguyen Trai, P1", "  ", false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createAddress(5L, addr("123 Nguyen Trai, P1", "c".repeat(101), false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        service.createAddress(5L, addr("123 Nguyen Trai, P1", "c".repeat(100), false), meAuth);
    }

    @Test
    void createAddress_nullRequest_is400() {
        assertThatThrownBy(() -> service.createAddress(5L, null, meAuth)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void createAddress_badType_is400_blankTypeIsAllowed() {
        assertThatThrownBy(() -> service.createAddress(5L, new UserAddressRequest("HOME", "123 Nguyen Trai, P1", "HCM", null, null, false), meAuth))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        var res = service.createAddress(5L, new UserAddressRequest(" ", "123 Nguyen Trai, P1", "HCM", null, null, false), meAuth);
        assertThat(res.addressType()).isNull();
        var billing = service.createAddress(5L, new UserAddressRequest(" billing ", "123 Nguyen Trai, P1", "HCM", null, null, false), meAuth);
        assertThat(billing.addressType()).isEqualTo("BILLING");
    }

    @Test
    void createAddress_tenthAllowed_eleventhRefused() {
        List<UserAddress> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(saved(i, me, false));
        }
        when(addresses.findByUserId(5L)).thenReturn(nine);
        service.createAddress(5L, addr("123 Nguyen Trai, P1", "HCM", false), meAuth);
        List<UserAddress> ten = new ArrayList<>(nine);
        ten.add(saved(10, me, false));
        when(addresses.findByUserId(5L)).thenReturn(ten);
        assertThatThrownBy(() -> service.createAddress(5L, addr("123 Nguyen Trai, P1", "HCM", false), meAuth))
            .satisfies(t -> {
                assertStatus(t, HttpStatus.BAD_REQUEST);
                assertThat(((ResponseStatusException) t).getReason()).contains("10");
            });
    }

    @Test
    void createAddress_asDefault_clearsPreviousDefaults() {
        UserAddress old = saved(1, me, true);
        when(addresses.findByUserIdAndDefaultAddressTrue(5L)).thenReturn(List.of(old));
        var res = service.createAddress(5L, addr("123 Nguyen Trai, P1", "HCM", true), meAuth);
        assertThat(res.isDefault()).isTrue();
        assertThat(old.getDefaultAddress()).isFalse();
        verify(addresses).saveAll(anyList());
    }

    @Test
    void createAddress_notDefault_leavesDefaultsAlone() {
        UserAddress old = saved(1, me, true);
        when(addresses.findByUserIdAndDefaultAddressTrue(5L)).thenReturn(List.of(old));
        service.createAddress(5L, addr("123 Nguyen Trai, P1", "HCM", null), meAuth);
        assertThat(old.getDefaultAddress()).isTrue();
    }

    @Test
    void updateAddress_ownedOnly_unknownIs404() {
        when(addresses.findByIdAndUserId(1L, 5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateAddress(5L, 1L, addr("123 Nguyen Trai, P1", "HCM", false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void updateAddress_cannotReachAnotherUsersAddressByGuessingItsId() {
        // address 9 belongs to bob; the lookup is scoped to the caller (5), so it is not found
        when(addresses.findByIdAndUserId(9L, 5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateAddress(5L, 9L, addr("123 Nguyen Trai, P1", "HCM", false), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.deleteAddress(5L, 9L, meAuth)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
        verify(addresses, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    void updateAddress_makeDefault_keepsThisOneAndClearsOthers() {
        UserAddress mine = saved(1, me, false);
        UserAddress oldDefault = saved(2, me, true);
        when(addresses.findByIdAndUserId(1L, 5L)).thenReturn(Optional.of(mine));
        when(addresses.findByUserIdAndDefaultAddressTrue(5L)).thenReturn(List.of(oldDefault, mine));
        service.updateAddress(5L, 1L, addr("456 Le Loi, P2", "HCM", true), meAuth);
        assertThat(mine.getDefaultAddress()).isTrue();
        assertThat(oldDefault.getDefaultAddress()).isFalse();
        assertThat(mine.getStreet()).isEqualTo("456 Le Loi, P2");
    }

    @Test
    void updateAddress_invalidPayload_leavesTheStoredAddressUntouched() {
        UserAddress mine = saved(1, me, false);
        when(addresses.findByIdAndUserId(1L, 5L)).thenReturn(Optional.of(mine));
        assertThatThrownBy(() -> service.updateAddress(5L, 1L, addr("short", "HCM", true), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(mine.getStreet()).isEqualTo("123 Nguyen Trai, P1");
    }

    @Test
    void deleteAddress_ownedAddress_isDeletedByIdAndUser() {
        when(addresses.findByIdAndUserId(1L, 5L)).thenReturn(Optional.of(saved(1, me, false)));
        service.deleteAddress(5L, 1L, meAuth);
        verify(addresses).deleteByIdAndUserId(1L, 5L);
    }

    @Test
    void getAddresses_sortedById() {
        when(addresses.findByUserId(5L)).thenReturn(List.of(saved(3, me, false), saved(1, me, false), saved(2, me, false)));
        assertThat(service.getAddresses(5L, meAuth)).extracting(a -> a.id()).containsExactly(1L, 2L, 3L);
    }

    // ------------------------------------------------------------------ preferences

    @Test
    void preferences_defaultWhenNoneSaved() {
        var p = service.getPreferences(5L, meAuth);
        assertThat(p.theme()).isEqualTo("system");
        assertThat(p.notificationsEnabled()).isTrue();
    }

    @Test
    void preferences_upsertCreatesAndNormalisesTheme() {
        var p = service.upsertPreferences(5L, new UserPreferenceRequest(" DARK ", false), meAuth);
        assertThat(p.theme()).isEqualTo("dark");
        assertThat(p.notificationsEnabled()).isFalse();
        assertThat(p.userId()).isEqualTo(5L);
    }

    @Test
    void preferences_badTheme_is400_noSave() {
        assertThatThrownBy(() -> service.upsertPreferences(5L, new UserPreferenceRequest("neon", null), meAuth)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(prefs, never()).save(any());
    }

    @Test
    void preferences_partialUpdate_keepsOtherValue_nullRequestIsNoOp() {
        UserPreference existing = new UserPreference();
        existing.setUser(me);
        existing.setTheme("light");
        existing.setNotificationsEnabled(false);
        when(prefs.findByUserId(5L)).thenReturn(Optional.of(existing));
        service.upsertPreferences(5L, new UserPreferenceRequest(null, true), meAuth);
        assertThat(existing.getTheme()).isEqualTo("light");
        assertThat(existing.getNotificationsEnabled()).isTrue();
        service.upsertPreferences(5L, null, meAuth);
        assertThat(existing.getTheme()).isEqualTo("light");
    }

    @Test
    void preferences_staffMayChangeTheirOwnTheme() {
        Authentication rootAuth = Fx.auth("root");
        var p = service.upsertPreferences(1L, new UserPreferenceRequest("light", null), rootAuth);
        assertThat(p.userId()).isEqualTo(1L);
    }
}
