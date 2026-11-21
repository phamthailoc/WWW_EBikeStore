package com.ebike.service.admin;

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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Deletes an account at the customer's request without deleting the shop's records (V6).
 *
 * <p>The row stays with status {@code DELETED}: nobody can sign in, and orders, shipments and payments keep pointing at it
 * because sales records must be kept for accounting. Everything that identifies the person on the account is removed:
 * name, saved addresses, favourites, preferences, notifications, back-in-stock requests, the phone number, words and shop's note of their test-ride bookings (the bookings stay, and the open ones are cancelled so the places are free again), the text of their warranty requests (the requests and the shop's repair notes stay, like orders) and the
 * reviews they wrote (product ratings are recomputed). Email and username become
 * {@code deleted_<id>...} so the person can register again with the same ones. The shop's own note on a warranty request (staff_note) is
 * kept as part of the repair record, so staff must not write a customer's phone number or address in it. The name, phone and address printed on
 * past orders are part of the sales record and stay as they are.
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
        this.users = users;
        this.reviews = reviews;
        this.passwordEncoder = passwordEncoder;
        this.clock = Clock.system(ZoneId.of(timeZone));
    }

    /** Runs inside the caller's transaction; the caller saves the user. */
    public void erase(User user) {
        Long id = user.getId();
        // V23: lock the account row first. A booking in progress holds the showroom row and then this row, so taking this one before the bookings are
        // updated keeps both in the same order (no deadlock); the booking then finds the account gone and is refused.
        users.findLockedById(id);
        addresses.deleteAll(addresses.findByUserId(id));
        favorites.deleteAll(favorites.findByUserId(id));
        preferences.findByUserId(id).ifPresent(preferences::delete);
        notifications.deleteAll(notifications.findByUserIdOrderByCreatedAtDesc(id));
        stockAlerts.deleteByUserId(id);
        warrantyClaims.clearDescriptionsOf(id, ERASED_DESCRIPTION);
        // V23: drop the phone number and the words of every booking, then cancel the open ones so their places are free (the rows stay as the shop's
        // record; the cancellation carries the code ERASED, which each page words in its own language)
        LocalDateTime now = LocalDateTime.now(clock);
        testRides.clearPersonalDataOf(id, now);
        testRides.cancelOpenOf(id, now);
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
