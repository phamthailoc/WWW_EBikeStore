package com.ebike.service.admin;

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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deletes an account at the customer's request without deleting the shop's records (V6).
 *
 * <p>The row stays with status {@code DELETED}: nobody can sign in, and orders, shipments and payments keep pointing at it
 * because sales records must be kept for accounting. Everything that identifies the person on the account is removed:
 * name, saved addresses, favourites, preferences, notifications, back-in-stock requests, the phone number, words of their test-ride bookings (the bookings and the shop's note on them stay, and the open ones are cancelled so the places are free again), the text of their warranty requests (the requests and the shop's repair notes stay, like orders; a request still open is closed) and the
 * reviews they wrote (product ratings are recomputed), and the ID number and income they declared on an instalment application (the plan and the staff's verdict stay, like orders). Email and username become
 * {@code deleted_<id>...} so the person can register again with the same ones. The shop's own note on a warranty request (staff_note) is
 * kept as part of the repair record, so staff must not write a customer's phone number or address in it. The name, phone and address printed on
 * past orders are part of the sales record and stay as they are; the email typed at checkout stays on the order too, but a receipt or warranty card of an erased account never prints an email.
 */
@Component
public class AccountErasure {
    public static final String DELETED = "DELETED";
    /** What replaces the text of a warranty request: empty, so the staff page can show the marker in its own language (a description is never empty otherwise). */
    public static final String ERASED_DESCRIPTION = "";

    private final UserAddressRepository addresses;
    private final UserFavoriteRepository favorites;
    private final UserPreferenceRepository preferences;
    private final NotificationRepository notifications;
    private final StockAlertRepository stockAlerts;
    private final WarrantyClaimRepository warrantyClaims;
    private final TestRideBookingRepository testRides;
    private final InstallmentApplicationRepository installments;
    private final UserRepository users;
    private final ProductReviewService reviews;
    private final PasswordEncoder passwordEncoder;
    /** The showroom's clock ({@code app.time-zone}): the cancellation stamps match the booking times, which are the showroom's local time. */
    private final Clock clock;

    public AccountErasure(
        UserAddressRepository addresses,
        UserFavoriteRepository favorites,
        UserPreferenceRepository preferences,
        NotificationRepository notifications,
        StockAlertRepository stockAlerts,
        WarrantyClaimRepository warrantyClaims,
        TestRideBookingRepository testRides,
        InstallmentApplicationRepository installments,
        UserRepository users,
        ProductReviewService reviews,
        PasswordEncoder passwordEncoder,
        @Value("${app.time-zone:Asia/Ho_Chi_Minh}") String timeZone
    ) {
        this.addresses = addresses;
        this.favorites = favorites;
        this.preferences = preferences;
        this.notifications = notifications;
        this.stockAlerts = stockAlerts;
        this.warrantyClaims = warrantyClaims;
        this.testRides = testRides;
        this.installments = installments;
        this.users = users;
        this.reviews = reviews;
        this.passwordEncoder = passwordEncoder;
        this.clock = Clock.system(ZoneId.of(timeZone));
    }

    /** Runs inside the caller's transaction; the caller saves the user. */
    public void erase(User user) {
        erase(user, false);
    }

    /** {@code byOwner}: the customer deletes their own account, so a refusal is worded for them and not for the staff. */
    public void erase(User user, boolean byOwner) {
        Long id = user.getId();
        // V23: lock the account row first. A booking in progress holds the showroom row and then this row, so taking this one before the bookings are
        // updated keeps both in the same order (no deadlock); the booking then finds the account gone and is refused.
        users.findLockedById(id);
        // V24: an application waiting for the staff, or for its down payment, still holds an order's stock and someone has to answer it: the order is
        // cancelled (or the application rejected) first. Counted after the lock, so an order placed at this very moment cannot slip in.
        if (installments.countOpenOf(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, byOwner ? "Bạn còn đơn mua trả góp đang chờ duyệt hoặc chờ trả trước. Hãy hủy đơn đó trong mục Đơn hàng trước khi xóa tài khoản." : "Khách này còn hồ sơ trả góp đang mở (chờ duyệt hoặc chờ trả trước). Hãy từ chối hồ sơ hoặc hủy đơn trước khi xóa tài khoản.");
        }
        addresses.deleteAll(addresses.findByUserId(id));
        favorites.deleteAll(favorites.findByUserId(id));
        preferences.findByUserId(id).ifPresent(preferences::delete);
        notifications.deleteAll(notifications.findByUserIdOrderByCreatedAtDesc(id));
        stockAlerts.deleteByUserId(id);
        warrantyClaims.clearDescriptionsOf(id, ERASED_DESCRIPTION);
        // an open warranty request has nobody left to answer: it is closed, and the shop keeps its notes
        warrantyClaims.closeOpenOf(id, "Khách đã xóa tài khoản nên yêu cầu được đóng.");
        // V23: drop the phone number and the words of every booking, then cancel the open ones so their places are free (the rows stay as the shop's
        // record; the cancellation carries the code ERASED, which each page words in its own language)
        LocalDateTime now = LocalDateTime.now(clock);
        testRides.clearPersonalDataOf(id, now);
        testRides.cancelOpenOf(id, now);
        // V24: the ID number and the income of an instalment application are personal data; the plan and the staff verdict stay as the shop's record
        // (an application still open blocks the erasure above, so none is waiting for a decision)
        installments.clearPersonalDataOf(id, now);
        reviews.removeReviewsOf(id);

        user.setUsername("deleted_" + id);
        user.setEmail("deleted_" + id + "@deleted.invalid");
        user.setFirstName("Tài khoản");
        user.setLastName("đã xóa");
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setVerified(false);
        user.setEmailVerifiedAt(null);
        user.setActive(false);
        user.setAccountStatus(DELETED);
    }
}
