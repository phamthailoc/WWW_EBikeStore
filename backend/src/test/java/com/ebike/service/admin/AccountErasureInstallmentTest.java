package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
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

/** V24: erasing an account removes the ID number and the income it declared on instalment applications (the plan and the verdict stay, like orders). */
class AccountErasureInstallmentTest {

    @Test
    void erasingAnAccountClearsTheIdNumberAndIncomeOfItsApplicationsAfterLockingTheAccount() {
        InstallmentApplicationRepository installments = mock(InstallmentApplicationRepository.class);
        UserRepository users = mock(UserRepository.class);
        AccountErasure erasure = new AccountErasure(
            mock(UserAddressRepository.class), mock(UserFavoriteRepository.class), mock(UserPreferenceRepository.class),
            mock(NotificationRepository.class), mock(StockAlertRepository.class), mock(WarrantyClaimRepository.class),
            mock(TestRideBookingRepository.class), installments, users, mock(ProductReviewService.class), mock(PasswordEncoder.class), "Asia/Ho_Chi_Minh");
        User user = new User();
        user.setId(42L);

        erasure.erase(user);

        var order = inOrder(users, installments);
        order.verify(users).findLockedById(42L);
        order.verify(installments).clearPersonalDataOf(eq(42L), any(LocalDateTime.class));
        assertEquals(AccountErasure.DELETED, user.getAccountStatus());
    }
}
