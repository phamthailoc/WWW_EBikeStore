package com.ebike.service.stock;

import com.ebike.config.CacheNames;
import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.StockAlert;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.repository.product.StockAlertRecipient;
import com.ebike.repository.product.StockAlertRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V22: tells every customer waiting for a colour. A separate bean so that callers outside it (after-commit hooks, the
 * sweep) go through the transaction proxy. Per colour it costs a fixed number of queries however many customers wait:
 * one locking read of the waiting rows, one join for their accounts and languages, one read of which are customers,
 * and one UPDATE (plus one DELETE when needed).
 */
@Service
public class StockAlertDelivery {

    private final StockAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository variantRepository;
    private final EntityManager entityManager;
    private final CacheManager cacheManager;

    public StockAlertDelivery(
        StockAlertRepository alertRepository,
        UserRepository userRepository,
        ProductVariantRepository variantRepository,
        EntityManager entityManager,
        CacheManager cacheManager
    ) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.variantRepository = variantRepository;
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
        // One join gives every waiting customer's account state and language; no read per customer.
        List<StockAlertRecipient> recipients = alertRepository.findRecipients(waiting.stream().map(StockAlert::getId).toList());
        Set<Long> userIds = recipients.stream().map(StockAlertRecipient::userId).collect(Collectors.toSet());

        Product product = variant.getProduct();
        String colour = variant.getColorName() != null ? variant.getColorName() : variant.getVariantName();
        LocalDateTime now = LocalDateTime.now();
        // Only customers can see and cancel their rows (the API is closed to staff): a customer whose role was changed
        // since (for example promoted to Manager) loses the row instead of getting notices they cannot manage.
        Set<Long> customerIds = new HashSet<>(userRepository.findCustomerIds(userIds));
        Set<Long> toDelete = new HashSet<>();
        Set<Long> toMark = new HashSet<>();
        int told = 0;
        for (StockAlertRecipient recipient : recipients) {
            if (!customerIds.contains(recipient.userId())) {
                toDelete.add(recipient.alertId());
                continue;
            }
            if (!StockAlertRules.canReceive(recipient.accountStatus(), recipient.active())) {
                // not a working account (erased or disabled by an admin): the row stays armed in case the account comes back
                continue;
            }
            toMark.add(recipient.alertId());
            NotificationEventBridge.publish(new StockAlertTriggeredEvent(
                recipient.userId(),
                product.getName(),
                colour,
                product.getSlug(),
                StockAlertMessages.normalize(recipient.language())
            ));
            told++;
        }
        if (!toDelete.isEmpty()) {
            alertRepository.deleteByIds(toDelete);
        }
        if (!toMark.isEmpty()) {
            alertRepository.markNotified(toMark, now);
        }
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
