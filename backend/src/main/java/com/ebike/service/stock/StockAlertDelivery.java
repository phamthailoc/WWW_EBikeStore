package com.ebike.service.stock;

import com.ebike.config.CacheNames;
import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.StockAlert;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.repository.product.StockAlertRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V22: tells every customer waiting for a colour. A separate bean so that callers outside it (after-commit hooks, the
 * sweep) go through the transaction proxy. Per colour it costs a fixed number of queries however many customers wait:
 * one locking read of the waiting rows, one read of their accounts, one read of their preferences and one batched
 * update.
 */
@Service
public class StockAlertDelivery {

    private final StockAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository variantRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final EntityManager entityManager;
    private final CacheManager cacheManager;

    public StockAlertDelivery(
        StockAlertRepository alertRepository,
        UserRepository userRepository,
        ProductVariantRepository variantRepository,
        UserPreferenceRepository preferenceRepository,
        EntityManager entityManager,
        CacheManager cacheManager
    ) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.variantRepository = variantRepository;
        this.preferenceRepository = preferenceRepository;
        this.entityManager = entityManager;
        this.cacheManager = cacheManager;
    }

    // A new transaction even when called from an after-commit hook, where the finished stock transaction is still bound to the thread.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int deliver(Long variantId) {
        return variantRepository.findWithProductById(variantId)
            .map(variant -> {
                // With open-in-view the variant may already sit in this thread's persistence context with an old stock.
                entityManager.refresh(variant);
                return deliver(variant);
            })
            .orElse(0);
    }

    /**
     * Returns how many customers were told. The rows are locked while they are marked as used, so two managers saving at
     * once, or the sweep running at the same moment, tell each customer only once.
     */
    // A new transaction even when called from an after-commit hook, where the finished stock transaction is still bound to the thread.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int deliver(ProductVariant variant) {
        // A colour or bike that is no longer sold, or has nothing left, must not invite anyone to buy it; the rows stay armed for later.
        if (variant == null || !StockAlertRules.canNotify(
            Boolean.TRUE.equals(variant.getActive()),
            variant.getProduct() != null && Boolean.TRUE.equals(variant.getProduct().getActive()),
            variant.getStockQuantity())) {
            return 0;
        }
        List<StockAlert> waiting = alertRepository.lockWaitingByVariantId(variant.getId());
        if (waiting.isEmpty()) {
            return 0;
        }
        Set<Long> userIds = waiting.stream()
            .map(alert -> alert.getUser() == null ? null : alert.getUser().getId())
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(user -> users.put(user.getId(), user));
        Map<Long, String> languages = new HashMap<>();
        for (UserPreference preference : preferenceRepository.findByUserIdIn(userIds)) {
            languages.put(preference.getUser().getId(), preference.getLanguage());
        }

        Product product = variant.getProduct();
        String colour = variant.getColorName() != null ? variant.getColorName() : variant.getVariantName();
        LocalDateTime now = LocalDateTime.now();
        int told = 0;
        for (StockAlert alert : waiting) {
            User user = alert.getUser() == null ? null : users.get(alert.getUser().getId());
            if (user == null || !StockAlertRules.canReceive(user.getAccountStatus(), user.getActive())) {
                // not a working account (erased or disabled by an admin): the row stays armed in case the account comes back
                continue;
            }
            alert.setNotifiedAt(now);
            NotificationEventBridge.publish(new StockAlertTriggeredEvent(
                user.getId(),
                product.getName(),
                colour,
                product.getSlug(),
                StockAlertMessages.normalize(languages.get(user.getId()))
            ));
            told++;
        }
        alertRepository.saveAll(waiting);
        if (told > 0) {
            evictProductPages();
        }
        return told;
    }

    /**
     * The customer follows the link in the notice to the bike page, which must not still say "sold out". The stock
     * update evicts these caches too, but only after this code ran, and a colour put back on sale by the sweep never
     * had an eviction. The cache manager is transaction aware: this clears after this transaction commits.
     */
    private void evictProductPages() {
        for (String name : List.of(CacheNames.PRODUCT_LIST_CACHE, CacheNames.PRODUCT_DETAIL_CACHE)) {
            org.springframework.cache.Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
