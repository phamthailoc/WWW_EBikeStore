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
import java.time.LocalDateTime;
import java.util.List;
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

    public StockAlertService(
        StockAlertRepository alertRepository,
        UserRepository userRepository,
        ProductVariantRepository variantRepository
    ) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional(readOnly = true)
    public List<StockAlertResponse> list(String username) {
        requireUsername(username);
        return alertRepository.findByUserUsernameOrderByCreatedAtDesc(username).stream()
            .filter(alert -> alert.getVariant().getProduct() != null
                && Boolean.TRUE.equals(alert.getVariant().getProduct().getActive()))
            .map(this::toResponse)
            .toList();
    }

    /** Subscribing twice keeps one row. A row already used for an earlier restock is armed again. */
    @Transactional
    public StockAlertResponse subscribe(String username, Long variantId) {
        requireUsername(username);
        if (variantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn màu xe.");
        }
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        ProductVariant variant = variantRepository.findById(variantId)
            .filter(candidate -> Boolean.TRUE.equals(candidate.getActive())
                && candidate.getProduct() != null && Boolean.TRUE.equals(candidate.getProduct().getActive()))
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
        return toResponse(alertRepository.save(alert));
    }

    @Transactional
    public void unsubscribe(String username, Long variantId) {
        requireUsername(username);
        userRepository.findByUsername(username)
            .flatMap(user -> alertRepository.findByUserIdAndVariantId(user.getId(), variantId))
            .ifPresent(alertRepository::delete);
    }

    /** Called by the manager stock update after it moved a colour from sold out to in stock. */
    @Transactional
    public int notifyRestocked(ProductVariant variant) {
        List<StockAlert> waiting = alertRepository.findByVariantIdAndNotifiedAtIsNull(variant.getId());
        Product product = variant.getProduct();
        LocalDateTime now = LocalDateTime.now();
        for (StockAlert alert : waiting) {
            alert.setNotifiedAt(now);
            NotificationEventBridge.publish(new StockAlertTriggeredEvent(
                alert.getUser().getId(),
                product.getName(),
                variant.getColorName() != null ? variant.getColorName() : variant.getVariantName(),
                product.getSlug()
            ));
        }
        alertRepository.saveAll(waiting);
        return waiting.size();
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
