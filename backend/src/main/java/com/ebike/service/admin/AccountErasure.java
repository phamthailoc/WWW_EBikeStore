package com.ebike.service.admin;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.notification.NotificationRepository;
import com.ebike.repository.user.UserAddressRepository;
import com.ebike.repository.user.UserFavoriteRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.product.ProductReviewService;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Deletes an account at the customer's request without deleting the shop's records (V6).
 *
 * <p>The row stays with status {@code DELETED}: nobody can sign in, and orders, shipments and payments keep pointing at it
 * because sales records must be kept for accounting. Everything that identifies the person on the account is removed:
 * name, saved addresses, favourites, preferences, notifications and the reviews they wrote (product ratings are
 * recomputed). Email and username become
 * {@code deleted_<id>...} so the person can register again with the same ones. The name, phone and address printed on
 * past orders are part of the sales record and stay as they are.
 */
@Component
public class AccountErasure {
    public static final String DELETED = "DELETED";

    private final UserAddressRepository addresses;
    private final UserFavoriteRepository favorites;
    private final UserPreferenceRepository preferences;
    private final NotificationRepository notifications;
    private final ProductReviewService reviews;
    private final PasswordEncoder passwordEncoder;

    public AccountErasure(
        UserAddressRepository addresses,
        UserFavoriteRepository favorites,
        UserPreferenceRepository preferences,
        NotificationRepository notifications,
        ProductReviewService reviews,
        PasswordEncoder passwordEncoder
    ) {
        this.addresses = addresses;
        this.favorites = favorites;
        this.preferences = preferences;
        this.notifications = notifications;
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
