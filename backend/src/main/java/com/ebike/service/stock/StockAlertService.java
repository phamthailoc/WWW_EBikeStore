package com.ebike.service.stock;

import com.ebike.dto.product.response.StockAlertResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.StockAlert;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.product.ProductVariantRepository;
import com.ebike.repository.product.StockAlertRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** V22: "tell me when this colour is back". */
@Service
public class StockAlertService {

    private static final Logger log = LoggerFactory.getLogger(StockAlertService.class);

    private final StockAlertRepository alertRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository variantRepository;
    private final StockAlertDelivery delivery;

    public StockAlertService(
        StockAlertRepository alertRepository,
        UserRepository userRepository,
        ProductVariantRepository variantRepository,
        StockAlertDelivery delivery
    ) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.variantRepository = variantRepository;
        this.delivery = delivery;
    }

    @Transactional(readOnly = true)
    public List<StockAlertResponse> list(String username) {
        requireUsername(username);
        return alertRepository.findResponsesByUsername(username);
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
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
        // scalar reads only: the bike is never loaded as an entity (its specification would cost one more SELECT)
        Integer stock = variantRepository.findStockOnSale(variantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy màu xe này."));

        StockAlert alert = alertRepository.findByUserIdAndVariantId(user.getId(), variantId).orElse(null);
        if (alert != null && alert.getNotifiedAt() == null) {
            return responseOf(user.getId(), variantId);
        }
        if (!StockAlertRules.canSubscribe(stock)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Màu xe này đang còn hàng, bạn có thể đặt mua ngay.");
        }
        if (alert == null) {
            alert = new StockAlert();
            alert.setUser(user);
            alert.setVariant(variantRepository.getReferenceById(variantId));
        }
        alert.setNotifiedAt(null);
        try {
            alertRepository.save(alert);
            tellIfRestockedMeanwhile(variantId);
            return responseOf(user.getId(), variantId);
        } catch (DataIntegrityViolationException duplicate) {
            // another request created the same (customer, colour) row first: answer with that one
            return alertRepository.findResponseByUserIdAndVariantId(user.getId(), variantId).orElseThrow(() -> duplicate);
        }
    }

    /**
     * A manager may commit a restock between the moment this customer read "sold out" and the moment the row became
     * visible, so the restock never saw the row. Reading the stock again after saving closes that gap: if the colour is
     * back on sale already, the customer is told right away instead of waiting for the periodic sweep.
     */
    private void tellIfRestockedMeanwhile(Long variantId) {
        // A scalar query, not findWithProductById: with open-in-view that would return the entity already loaded (stock 0).
        if (variantRepository.isOnSaleAndInStock(variantId)) {
            tellWaiting(variantId);
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
     * stock update, cancelled order) and by the periodic sweep.
     */
    public void notifyRestocked(ProductVariant variant) {
        if (variant == null || variant.getId() == null) {
            return;
        }
        Long variantId = variant.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // after the stock change is committed, so the customers see the new stock and a failure here cannot undo it
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tellWaiting(variantId);
                }
            });
        } else {
            tellWaiting(variantId);
        }
    }

    private int tellWaiting(Long variantId) {
        try {
            return delivery.deliver(variantId);
        } catch (RuntimeException exception) {
            log.warn("Could not tell the customers waiting for colour {}: {}", variantId, exception.getMessage());
            return 0;
        }
    }

    /** Same as above for the sweep, which only has the colour's id; runs in its own transaction per colour. */
    public int notifyRestockedById(Long variantId) {
        return tellWaiting(variantId);
    }

    /** Colours with someone waiting that are in stock and on sale (for example put back on sale after being sold out). */
    @Transactional(readOnly = true)
    public List<Long> waitingVariantIdsInStock() {
        return alertRepository.findWaitingVariantIdsInStock();
    }

    /** The row as the page shows it, read fresh: never an entity that open-in-view may already hold with old values. */
    private StockAlertResponse responseOf(Long userId, Long variantId) {
        return alertRepository.findResponseByUserIdAndVariantId(userId, variantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu báo hàng."));
    }

    private void requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để tiếp tục.");
        }
    }
}
