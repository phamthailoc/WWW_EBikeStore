package com.ebike.service.admin;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.product.StockAlertRepository;
import com.ebike.repository.user.UserAddressRepository;
import com.ebike.repository.user.UserFavoriteRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.product.ProductReviewService;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Deletes an account at the customer's request without deleting the shop's records (V6).
 *
 * <p>The row stays with status {@code DELETED}: nobody can sign in, and orders, shipments and payments keep pointing at it
 * because sales records must be kept for accounting. Everything that identifies the person on the account is removed:
 * name, saved addresses, favourites, preferences, notifications, back-in-stock requests, the text of their warranty requests (the requests and the shop's repair notes stay, like orders) and the
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
    private final ProductReviewService reviews;
    private final PasswordEncoder passwordEncoder;

    public AccountErasure(
        UserAddressRepository addresses,
        UserFavoriteRepository favorites,
        UserPreferenceRepository preferences,
        NotificationRepository notifications,
        StockAlertRepository stockAlerts,
        WarrantyClaimRepository warrantyClaims,
        ProductReviewService reviews,
        PasswordEncoder passwordEncoder
    ) {
        this.addresses = addresses;
        this.favorites = favorites;
        this.preferences = preferences;
        this.notifications = notifications;
        this.stockAlerts = stockAlerts;
        this.warrantyClaims = warrantyClaims;
        this.reviews = reviews;
        this.passwordEncoder = passwordEncoder;
    }

    /** Runs inside the caller's transaction; the caller saves the user. */
    public void erase(User user) {
        Long id = user.getId();
        addresses.deleteAll(addresses.findByUserId(id));
        favorites.deleteAll(favorites.findByUserId(id));
        preferences.findByUserId(id).ifPresent(preferences::delete);
        notifications.deleteAll(notifications.findByUserIdOrderByCreatedAtDesc(id));
        stockAlerts.deleteByUserId(id);
        warrantyClaims.clearDescriptionsOf(id, ERASED_DESCRIPTION);
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
