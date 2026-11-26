package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.auth.AuthenticationLog;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.product.StockAlertRepository;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.repository.user.UserAddressRepository;
import com.ebike.repository.user.UserFavoriteRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.product.ProductReviewService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AccountErasureTest {

    UserAddressRepository addresses = mock(UserAddressRepository.class);
    UserFavoriteRepository favorites = mock(UserFavoriteRepository.class);
    UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    StockAlertRepository stockAlerts = mock(StockAlertRepository.class);
    WarrantyClaimRepository warranty = mock(WarrantyClaimRepository.class);
    TestRideBookingRepository rides = mock(TestRideBookingRepository.class);
    InstallmentApplicationRepository installments = mock(InstallmentApplicationRepository.class);
    UserRepository users = mock(UserRepository.class);
    ProductReviewService reviews = mock(ProductReviewService.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    AccountErasure erasure;
    User user;

    @BeforeEach
    void setUp() {
        erasure = new AccountErasure(addresses, favorites, preferences, notifications, stockAlerts, warranty, rides, installments, users,
            reviews, encoder, "Asia/Ho_Chi_Minh");
        when(encoder.encode(anyString())).thenAnswer(i -> "ENC(" + i.getArgument(0) + ")");
        user = Fx.user(42L, "tien", "tien@example.com", "CUSTOMER");
        user.setFirstName("Le");
        user.setLastName("Tien");
        user.setVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void anonymisesEveryIdentifyingField_andDisablesTheAccount() {
        String oldHash = user.getPasswordHash();
        erasure.erase(user);
        assertThat(user.getUsername()).isEqualTo("deleted_42");
        assertThat(user.getEmail()).isEqualTo("deleted_42@deleted.invalid");
        assertThat(user.getFirstName()).isEqualTo("Tài khoản");
        assertThat(user.getLastName()).isEqualTo("đã xóa");
        assertThat(user.getActive()).isFalse();
        assertThat(user.getVerified()).isFalse();
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getAccountStatus()).isEqualTo(AccountErasure.DELETED);
        assertThat(user.getPasswordHash()).isNotEqualTo(oldHash).startsWith("ENC(");
        assertThat(user.toString() + user.getUsername() + user.getEmail() + user.getFirstName() + user.getLastName())
            .doesNotContain("tien@example.com").doesNotContain("Le Tien");
    }

    @Test
    void newPasswordIsRandom_twoErasuresNeverShareOne() {
        User other = Fx.user(43L, "bob", "bob@example.com", "CUSTOMER");
        erasure.erase(user);
        erasure.erase(other);
        assertThat(user.getPasswordHash()).isNotEqualTo(other.getPasswordHash());
        verify(encoder, org.mockito.Mockito.times(2)).encode(anyString());
        verify(encoder, never()).encode(eq("tien"));
    }

    @Test
    void removesPersonalRecords_butKeepsOrdersAndPayments() {
        when(preferences.findByUserId(42L)).thenReturn(Optional.of(new UserPreference()));
        erasure.erase(user);
        verify(addresses).deleteAll(any());
        verify(favorites).deleteAll(any());
        verify(preferences).delete(any(UserPreference.class));
        verify(notifications).deleteAll(any());
        verify(stockAlerts).deleteByUserId(42L);
        verify(reviews).removeReviewsOf(42L);
        verify(warranty).clearDescriptionsOf(eq(42L), eq(AccountErasure.ERASED_DESCRIPTION));
        verify(warranty).closeOpenOf(eq(42L), anyString());
        verify(rides).clearPersonalDataOf(eq(42L), any());
        verify(rides).cancelOpenOf(eq(42L), any());
        verify(installments).clearPersonalDataOf(eq(42L), any());
        verify(users, never()).delete(any());
        verify(users, never()).deleteById(any());
    }

    @Test
    void locksAccountRowFirst_beforeCountingOpenApplicationsOrTouchingBookings() {
        erasure.erase(user);
        var order = inOrder(users, installments, rides);
        order.verify(users).findLockedById(42L);
        order.verify(installments).countOpenOf(42L);
        order.verify(rides).clearPersonalDataOf(eq(42L), any());
    }

    @Test
    void openInstalmentApplication_blocksErasure_noDataTouched_staffWording() {
        when(installments.countOpenOf(42L)).thenReturn(1L);
        assertThatThrownBy(() -> erasure.erase(user)).isInstanceOf(ResponseStatusException.class)
            .satisfies(t -> {
                assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
                assertThat(((ResponseStatusException) t).getReason()).startsWith("Khách này");
            });
        verify(addresses, never()).deleteAll(any());
        verify(reviews, never()).removeReviewsOf(any());
        assertThat(user.getUsername()).isEqualTo("tien");
        assertThat(user.getAccountStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void openInstalmentApplication_ownerWording_addressesTheCustomer() {
        when(installments.countOpenOf(42L)).thenReturn(2L);
        assertThatThrownBy(() -> erasure.erase(user, true)).isInstanceOf(ResponseStatusException.class)
            .satisfies(t -> assertThat(((ResponseStatusException) t).getReason()).startsWith("Bạn còn"));
    }

    @Test
    void erasingTwice_isStableAndKeepsDeletedMarker() {
        erasure.erase(user);
        erasure.erase(user);
        assertThat(user.getUsername()).isEqualTo("deleted_42");
        assertThat(user.getAccountStatus()).isEqualTo("DELETED");
    }

    @Test
    void sameEmailAndUsernameCanRegisterAgain_placeholdersAreUniquePerId() {
        User other = Fx.user(43L, "tien2", "t2@example.com", "CUSTOMER");
        erasure.erase(user);
        erasure.erase(other);
        assertThat(user.getEmail()).isNotEqualTo(other.getEmail());
        assertThat(user.getUsername()).isNotEqualTo(other.getUsername());
    }

    /**
     * AccountErasure's own Javadoc: "Everything that identifies the person on the account is removed". The sign-in history keeps each
     * login's IP address and browser string (AuthenticationLog), which identify the person, and erase() never clears them
     * (AccountErasure.java:erase has no authentication-log step; admin delete does call deleteByUserId, the self-service path does not).
     * Expected: user.getAuthenticationLogs() is empty after the erasure. Actual: untouched.
     */
    @Test
    @Tag("bug")
    void bug_erasure_keepsLoginHistoryWithIpAddressAndUserAgent() {
        AuthenticationLog log = new AuthenticationLog();
        log.setUser(user);
        log.setIpAddress("203.0.113.9");
        log.setUserAgent("Mozilla/5.0 (secret device)");
        user.getAuthenticationLogs().add(log);
        erasure.erase(user);
        assertThat(user.getAuthenticationLogs()).isEmpty();
    }

    @Test
    void listsAreQueriedByTheUsersId() {
        when(addresses.findByUserId(42L)).thenReturn(List.of());
        erasure.erase(user);
        verify(addresses).findByUserId(42L);
        verify(favorites).findByUserId(42L);
        verify(notifications).findByUserIdOrderByCreatedAtDesc(42L);
    }
}
