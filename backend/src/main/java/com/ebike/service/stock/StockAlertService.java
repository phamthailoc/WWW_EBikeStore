package com.ebike.service.stock;

import com.ebike.dto.product.response.StockAlertResponse;
import com.ebike.event.notification.StockAlertTriggeredEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.StockAlert;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.repository.product.StockAlertRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.model.entity.user.UserPreference;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** V22: "tell me when this colour is back". */
@Service
public class StockAlertService {

    private final StockAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository variantRepository;
    private final UserPreferenceRepository preferenceRepository;

    public StockAlertService(
        StockAlertRepository alertRepository,
        UserRepository userRepository,
        ProductVariantRepository variantRepository,
        UserPreferenceRepository preferenceRepository
    ) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.variantRepository = variantRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional(readOnly = true)
    public List<StockAlertResponse> list(String username) {
        requireUsername(username);
        return alertRepository.findByUserUsernameOrderByCreatedAtDesc(username).stream()
            .filter(alert -> isOnSale(alert.getVariant()))
            .map(this::toResponse)
            .toList();
    }

    /**
     * Subscribing twice keeps one row. A row already used for an earlier restock is armed again.
     * Not one transaction on purpose: when two requests race to insert the same row the loser hits the unique key,
     * and that failure must not poison a surrounding transaction before the existing row is read back.
     */
    public StockAlertResponse subscribe(String username, Long variantId) {
        requireUsername(username);
        if (variantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn màu xe.");
        }
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        ProductVariant variant = variantRepository.findWithProductById(variantId)
            .filter(this::isOnSale)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy màu xe này."));

        StockAlert alert = alertRepository.findByUserIdAndVariantId(user.getId(), variant.getId()).orElse(null);
        if (alert != null && alert.getNotifiedAt() == null) {
            return toResponse(alert);
        }
        if (!StockAlertRules.canSubscribe(variant.getStockQuantity())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Màu xe này đang còn hàng, bạn có thể đặt mua ngay.");
        }
        if (alert == null) {
            alert = new StockAlert();
            alert.setUser(user);
            alert.setVariant(variant);
        }
        alert.setNotifiedAt(null);
        try {
            return toResponse(alertRepository.save(alert));
        } catch (DataIntegrityViolationException duplicate) {
            // another request created the same (customer, colour) row first: answer with that one
            return alertRepository.findByUserIdAndVariantId(user.getId(), variant.getId())
                .map(this::toResponse)
                .orElseThrow(() -> duplicate);
        }
    }

    @Transactional
    public void unsubscribe(String username, Long variantId) {
        requireUsername(username);
        userRepository.findByUsername(username)
            .flatMap(user -> alertRepository.findByUserIdAndVariantId(user.getId(), variantId))
            .ifPresent(alertRepository::delete);
    }

    /**
     * Tells every customer waiting for this colour. Called after the colour went from sold out to in stock (manager
     * stock update, cancelled order) and by the periodic sweep. Each row is claimed with one conditional update, so two
     * managers saving at once, or the sweep running at the same moment, tell each customer only once.
     */
    @Transactional
    public int notifyRestocked(ProductVariant variant) {
        // A colour or bike that is no longer sold, or has nothing left, must not invite anyone to buy it; the rows stay armed for later.
        if (variant == null || !StockAlertRules.canNotify(
            Boolean.TRUE.equals(variant.getActive()),
            variant.getProduct() != null && Boolean.TRUE.equals(variant.getProduct().getActive()),
            variant.getStockQuantity())) {
            return 0;
        }
        List<StockAlert> waiting = alertRepository.findByVariantIdAndNotifiedAtIsNull(variant.getId());
        Product product = variant.getProduct();
        LocalDateTime now = LocalDateTime.now();
        int told = 0;
        for (StockAlert alert : waiting) {
            User user = alert.getUser();
            if (user == null || "DELETED".equals(user.getAccountStatus())) {
                continue;
            }
            if (alertRepository.claim(alert.getId(), now) == 0) {
                continue;
            }
            NotificationEventBridge.publish(new StockAlertTriggeredEvent(
                user.getId(),
                product.getName(),
                variant.getColorName() != null ? variant.getColorName() : variant.getVariantName(),
                product.getSlug(),
                languageOf(user.getId())
            ));
            told++;
        }
        return told;
    }

    /** Same as above for the sweep, which only has the colour's id; runs in its own transaction per colour. */
    @Transactional
    public int notifyRestockedById(Long variantId) {
        return variantRepository.findWithProductById(variantId).map(this::notifyRestocked).orElse(0);
    }

    /** Colours with someone waiting that are in stock and on sale (for example put back on sale after being sold out). */
    @Transactional(readOnly = true)
    public List<Long> waitingVariantIdsInStock() {
        return alertRepository.findWaitingVariantIdsInStock();
    }

    private String languageOf(Long userId) {
        return StockAlertMessages.normalize(preferenceRepository.findByUserId(userId)
            .map(UserPreference::getLanguage)
            .orElse("vi"));
    }

    private boolean isOnSale(ProductVariant variant) {
        return variant != null && Boolean.TRUE.equals(variant.getActive())
            && variant.getProduct() != null && Boolean.TRUE.equals(variant.getProduct().getActive());
    }

    private StockAlertResponse toResponse(StockAlert alert) {
        ProductVariant variant = alert.getVariant();
        Product product = variant.getProduct();
        return new StockAlertResponse(
            alert.getId(),
            product.getId(),
            product.getName(),
            product.getSlug(),
            variant.getId(),
            variant.getColorName() != null ? variant.getColorName() : variant.getVariantName(),
            variant.getStockQuantity(),
            alert.getCreatedAt(),
            alert.getNotifiedAt()
        );
    }

    private void requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
    }
}
