package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.product.StockAlertRepository;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.repository.user.UserAddressRepository;
import com.ebike.repository.user.UserFavoriteRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.product.ProductReviewService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/** V23: erasing an account frees the test-ride places it held and removes the phone number and words it left on bookings. */
class AccountErasureTestRideTest {

    @Test
    void erasingAnAccountCancelsItsOpenTestRidesAndClearsItsPersonalData() {
        TestRideBookingRepository testRides = mock(TestRideBookingRepository.class);
        UserRepository users = mock(UserRepository.class);
        AccountErasure erasure = new AccountErasure(
            mock(UserAddressRepository.class), mock(UserFavoriteRepository.class), mock(UserPreferenceRepository.class),
            mock(NotificationRepository.class), mock(StockAlertRepository.class), mock(WarrantyClaimRepository.class), testRides, mock(com.ebike.repository.installment.InstallmentApplicationRepository.class),
            users, mock(ProductReviewService.class), mock(PasswordEncoder.class), "Asia/Ho_Chi_Minh");
        User user = new User();
        user.setId(42L);

        erasure.erase(user);

        var order = inOrder(users, testRides);
        // the account row is locked before any booking is updated, the order booking() uses
        order.verify(users).findLockedById(42L);
        order.verify(testRides).clearPersonalDataOf(eq(42L), any(LocalDateTime.class));
        order.verify(testRides).cancelOpenOf(eq(42L), any(LocalDateTime.class));
        assertEquals(AccountErasure.DELETED, user.getAccountStatus());
    }

    @Test
    void theCancellationIsStampedWithTheShowroomsClockNotTheServers() {
        TestRideBookingRepository testRides = mock(TestRideBookingRepository.class);
        // a zone 14 hours ahead of UTC: whatever zone the test machine runs in, only the configured zone can be within a minute of this stamp
        ZoneId zone = ZoneId.of("Pacific/Kiritimati");
        AccountErasure erasure = new AccountErasure(
            mock(UserAddressRepository.class), mock(UserFavoriteRepository.class), mock(UserPreferenceRepository.class),
            mock(NotificationRepository.class), mock(StockAlertRepository.class), mock(WarrantyClaimRepository.class), testRides, mock(com.ebike.repository.installment.InstallmentApplicationRepository.class),
            mock(UserRepository.class), mock(ProductReviewService.class), mock(PasswordEncoder.class), zone.getId());
        User user = new User();
        user.setId(7L);

        erasure.erase(user);

        ArgumentCaptor<LocalDateTime> stamp = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(testRides).cancelOpenOf(eq(7L), stamp.capture());
        assertTrue(Duration.between(stamp.getValue(), LocalDateTime.now(zone)).abs().toMinutes() < 1, stamp.getValue().toString());
    }
}
