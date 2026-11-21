package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.product.StockAlertRepository;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.repository.user.UserAddressRepository;
import com.ebike.repository.user.UserFavoriteRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.product.ProductReviewService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** V23: erasing an account frees the test-ride places it held and removes the phone number and words it left on bookings. */
class AccountErasureTestRideTest {

    @Test
    void erasingAnAccountCancelsItsOpenTestRidesAndClearsItsPersonalData() {
        TestRideBookingRepository testRides = mock(TestRideBookingRepository.class);
        AccountErasure erasure = new AccountErasure(
            mock(UserAddressRepository.class), mock(UserFavoriteRepository.class), mock(UserPreferenceRepository.class),
            mock(NotificationRepository.class), mock(StockAlertRepository.class), mock(WarrantyClaimRepository.class), testRides,
            mock(ProductReviewService.class), mock(PasswordEncoder.class));
        User user = new User();
        user.setId(42L);

        erasure.erase(user);

        var order = inOrder(testRides);
        order.verify(testRides).cancelOpenOf(eq(42L), any(LocalDateTime.class));
        order.verify(testRides).clearPersonalDataOf(eq(42L), any(LocalDateTime.class));
        assertEquals(AccountErasure.DELETED, user.getAccountStatus());
    }
}
