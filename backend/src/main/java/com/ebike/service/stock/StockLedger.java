package com.ebike.service.stock;

import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.stock.StockMovement;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.model.entity.stock.StockReceipt;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.stock.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.hibernate.Hibernate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * V26: the one place that writes the stock ledger and takes the row locks that every change of stock needs.
 *
 * <p>Locks. A change of stock reads the figure and writes it back, so two changes at the same moment (two customers ordering the last bike, a
 * cancellation and a receipt) used to overwrite each other. {@link #lock} takes the rows first, bikes before colours and each in id order, then
 * reads them again from the database; with every writer taking them in that order, none waits for another in a circle. The rows are taken after the
 * order's own row and the customer's row (those come first everywhere), so they sit at the bottom of the lock order.
 */
@Component
public class StockLedger {

    /** One change of stock that has been applied to the entities and still has to be written down. */
    public record Entry(Product product, ProductVariant variant, int change, int balanceAfter, StockMovementReason reason, String note) {

        /** The change as it is after the entities were updated: the colour's figure when there is a colour, else the bike's own. */
        public static Entry of(Product product, ProductVariant variant, int change, StockMovementReason reason, String note) {
            Integer balance = variant != null ? variant.getStockQuantity() : product.getStockQuantity();
            return new Entry(product, variant, change, balance == null ? 0 : balance, reason, note);
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    private final StockMovementRepository movements;
    private final UserRepository users;

    public StockLedger(StockMovementRepository movements, UserRepository users) {
        this.movements = movements;
        this.users = users;
    }

    /**
     * Locks and re-reads the given bikes and colours (and the bike of every colour), in a fixed order. Call it before reading a stock figure you are
     * about to change, and before changing anything on these rows: the re-read throws away changes not yet written. Rows this transaction already
     * holds are left as they are, so a second call (for example one order after another in a job) never loses what the first one changed.
     */
    public void lock(Collection<Product> products, Collection<ProductVariant> variants) {
        Map<Long, Product> bikes = new TreeMap<>();
        Map<Long, ProductVariant> colours = new TreeMap<>();
        if (products != null) {
            for (Product product : products) {
                addBike(bikes, product);
            }
        }
        if (variants != null) {
            for (ProductVariant variant : variants) {
                ProductVariant real = unproxy(variant);
                if (real == null || real.getId() == null) {
                    continue;
                }
                colours.putIfAbsent(real.getId(), real);
                addBike(bikes, real.getProduct());
            }
        }
        bikes.values().forEach(this::lockRow);
        colours.values().forEach(this::lockRow);
    }

    private void addBike(Map<Long, Product> bikes, Product product) {
        Product real = unproxy(product);
        if (real != null && real.getId() != null) {
            bikes.putIfAbsent(real.getId(), real);
        }
    }

    private void lockRow(Object entity) {
        if (!entityManager.contains(entity)) {
            throw new IllegalStateException("The stock row to lock is not loaded in this transaction");
        }
        if (entityManager.getLockMode(entity) == LockModeType.PESSIMISTIC_WRITE) {
            return;
        }
        // refresh() re-reads the row and drops unflushed changes, so write them first
        entityManager.flush();
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
    }

    @SuppressWarnings("unchecked")
    private static <T> T unproxy(T entity) {
        return entity == null ? null : (T) Hibernate.unproxy(entity);
    }

    /** Writes the entries down. {@code actor} is the shop's own staff member, or null when a customer or the system did it. */
    public void record(List<Entry> entries, Long orderId, String orderNumber, StockReceipt receipt, User actor) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<StockMovement> lines = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.change() == 0 || entry.product() == null) {
                continue;
            }
            StockMovement line = new StockMovement();
            line.setProduct(entry.product());
            line.setVariant(entry.variant());
            line.setQuantityChange(entry.change());
            line.setBalanceAfter(entry.balanceAfter());
            line.setReason(entry.reason());
            line.setOrderId(orderId);
            line.setOrderNumber(orderNumber);
            line.setReceipt(receipt);
            line.setActor(actor);
            line.setNote(StockRules.clean(entry.note()));
            lines.add(line);
        }
        if (!lines.isEmpty()) {
            movements.saveAll(lines);
        }
    }

    /** The signed-in member of staff making this request, or null when there is none (a customer's own order, the expiry job). */
    public User currentStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
            || authentication.getName().isBlank() || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return users.findByUsername(authentication.getName()).or(() -> users.findByEmail(authentication.getName())).orElse(null);
    }
}
