package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** V22: erasing an account also removes its back-in-stock requests. */
class AccountErasureStockAlertTest {

    @Test
    void erasingAnAccountDeletesItsStockAlerts() {
        StockAlertRepository stockAlerts = mock(StockAlertRepository.class);
        WarrantyClaimRepository warrantyClaims = mock(WarrantyClaimRepository.class);
        AccountErasure erasure = new AccountErasure(
            mock(UserAddressRepository.class), mock(UserFavoriteRepository.class), mock(UserPreferenceRepository.class),
            mock(NotificationRepository.class), stockAlerts, warrantyClaims, mock(TestRideBookingRepository.class), mock(UserRepository.class), mock(ProductReviewService.class), mock(PasswordEncoder.class));
        User user = new User();
        user.setId(42L);
        erasure.erase(user);
        verify(stockAlerts).deleteByUserId(42L);
        verify(warrantyClaims).clearDescriptionsOf(42L, AccountErasure.ERASED_DESCRIPTION);
        assertEquals(AccountErasure.DELETED, user.getAccountStatus());
    }
}
