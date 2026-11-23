package com.ebike.service.document;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.installment.InstallmentApplicationRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.warranty.WarrantyRules;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * V27: the sales receipt and the warranty card of an order, made as PDF when they are asked for (nothing is stored, so there is no copy to go
 * stale, to clean up when an account is erased, or to leak).
 *
 * <p>Who may read them: the customer who owns the order, and the staff who may see every order. A guest order has no owner, so only the staff
 * and the email sent at hand-over carry its documents. A stranger gets the same "not found" as an order that does not exist, so ids cannot be probed.
 */
@Service
public class OrderDocumentService {

    private static final Logger log = LoggerFactory.getLogger(OrderDocumentService.class);

    /** What the hand-over email carries: who gets it, in which language, and the files. */
    public record DeliveryDocuments(String email, String recipientName, String orderNumber, String language, List<PdfFile> files, boolean guest) {

        public DeliveryDocuments(String email, String recipientName, String orderNumber, String language, List<PdfFile> files) {
            this(email, recipientName, orderNumber, language, files, false);
        }
    }

    private final OrderRepository orders;
    private final InstallmentApplicationRepository installments;
    private final UserRepository users;
    private final UserPreferenceRepository preferences;
    private final StoreProfile store;
    private final PdfRenderer renderer;
    private final List<Period> intervals;
    private final TransactionTemplate transactions;

    public OrderDocumentService(
        OrderRepository orders,
        InstallmentApplicationRepository installments,
        UserRepository users,
        UserPreferenceRepository preferences,
        StoreProfile store,
        PdfRenderer renderer,
        PlatformTransactionManager transactionManager,
        @Value("${app.maintenance.intervals:" + WarrantyRules.DEFAULT_INTERVALS + "}") String intervals
    ) {
        this.orders = orders;
        this.installments = installments;
        this.users = users;
        this.preferences = preferences;
        this.store = store;
        this.renderer = renderer;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setReadOnly(true);
        this.intervals = WarrantyRules.parseIntervals(intervals);
    }

    /**
     * A page ready to become a PDF: everything read from the database is already in {@code model}. The render, which can take a while, is done after
     * the transaction has ended, so a slow render never holds a database connection.
     */
    private record Draft(String template, java.util.Map<String, Object> model, String filename) {
    }

    private record Pending(String email, String recipientName, String orderNumber, String language, List<Draft> drafts, boolean guest) {
    }

    /** The sales receipt of an order that is not cancelled. Before the bike is handed over it is marked "provisional". */
    public PdfFile receipt(Long orderId, Authentication authentication, String language) {
        return receipt(orderId, authentication, language, language);
    }

    /** {@code language} is the one of the document, {@code pageLanguage} the one of the page asking (the texts of an error). */
    public PdfFile receipt(Long orderId, Authentication authentication, String language, String pageLanguage) {
        Draft draft = reading(() -> {
            Order order = readable(orderId, authentication, pageLanguage);
            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw conflict(pageLanguage, "error.cancelled");
            }
            return receiptDraft(order, language);
        });
        return render(draft, pageLanguage);
    }

    /** The warranty card of the vehicles of a delivered order, one page each; {@code unitId} narrows it to one vehicle. */
    public PdfFile warrantyCard(Long orderId, Long unitId, Authentication authentication, String language) {
        return warrantyCard(orderId, unitId, authentication, language, language);
    }

    public PdfFile warrantyCard(Long orderId, Long unitId, Authentication authentication, String language, String pageLanguage) {
        Draft draft = reading(() -> {
            Order order = readable(orderId, authentication, pageLanguage);
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw conflict(pageLanguage, "error.notDelivered");
            }
            List<OrderItemUnit> units = unitsOf(order);
            if (unitId != null) {
                units = units.stream().filter(unit -> unitId.equals(unit.getId())).toList();
                if (units.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, DocumentText.text(pageLanguage, "error.unitNotFound"));
                }
            }
            if (units.isEmpty()) {
                throw conflict(pageLanguage, "error.noNumbers");
            }
            return cardDraft(order, units, language, unitId == null ? null : units.get(0).getFrameNumber());
        });
        return render(draft, pageLanguage);
    }

    /**
     * What to email when an order was handed over: the receipt, plus the warranty cards of the vehicles that have numbers. Empty when there is
     * nobody to write to (no address), or the account was erased since; the caller must not fail the order because of it.
     */
    public Optional<DeliveryDocuments> forDeliveredOrder(Long orderId) {
        Pending pending = reading(() -> pendingDelivery(orderId)).orElse(null);
        if (pending == null) {
            return Optional.empty();
        }
        List<PdfFile> files = new ArrayList<>();
        for (Draft draft : pending.drafts()) {
            files.add(render(draft, pending.language()));
        }
        return Optional.of(new DeliveryDocuments(pending.email(), pending.recipientName(), pending.orderNumber(), pending.language(), files, pending.guest()));
    }

    private Optional<Pending> pendingDelivery(Long orderId) {
        Order order = orders.findForDocuments(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.DELIVERED) {
            return Optional.empty();
        }
        User user = order.getUser();
        if (user != null && AccountErasure.DELETED.equals(user.getAccountStatus())) {
            return Optional.empty();
        }
        String email = DocumentModels.contactEmail(order);
        if (email == null) {
            return Optional.empty();
        }
        // the language the order was placed in wins (the profile row always has a language, "vi" by default, even when the customer never chose one);
        // an older order that recorded none follows the profile, and a guest order without one is Vietnamese, the shop's base language
        String language = order.getCustomerLanguage() != null && !order.getCustomerLanguage().isBlank() || user == null
            ? DocumentFormat.language(order.getCustomerLanguage())
            : preferences.findByUserId(user.getId()).map(saved -> DocumentFormat.language(saved.getLanguage())).orElse("vi");
        List<Draft> drafts = new ArrayList<>();
        drafts.add(receiptDraft(order, language));
        List<OrderItemUnit> units = unitsOf(order);
        if (!units.isEmpty()) {
            drafts.add(cardDraft(order, units, language, null));
        }
        String name = DocumentModels.customerName(order, language);
        return Optional.of(new Pending(email, "—".equals(name) ? DocumentText.text(language, "mail.guestName") : name, order.getOrderNumber(), language, drafts, user == null));
    }

    private <T> T reading(java.util.function.Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    // the issue time is read like the times stored on the order (the JVM zone), so the dates on one page always agree with each other
    private Draft receiptDraft(Order order, String language) {
        var installment = installments.findByOrderId(order.getId()).orElse(null);
        var model = DocumentModels.receipt(order, installment, store, language, LocalDateTime.now());
        return new Draft("receipt", model, "receipt-" + PdfFile.safeName(order.getOrderNumber()) + ".pdf");
    }

    private Draft cardDraft(Order order, List<OrderItemUnit> units, String language, String singleFrame) {
        var model = DocumentModels.warrantyCards(order, units, store, intervals, language, LocalDateTime.now());
        String name = singleFrame == null ? PdfFile.safeName(order.getOrderNumber()) : PdfFile.safeName(singleFrame);
        return new Draft("warranty-card", model, "warranty-card-" + name + ".pdf");
    }

    private PdfFile render(Draft draft, String language) {
        try {
            return new PdfFile(draft.filename(), renderer.render(draft.template(), draft.model()));
        } catch (RuntimeException exception) {
            log.error("Could not render {} for order {}", draft.template(), draft.model().get("orderNumber"), exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, DocumentText.text(language, "error.render"));
        }
    }

    /** The vehicles with numbers, in the order of the order's lines (then by id). */
    private static List<OrderItemUnit> unitsOf(Order order) {
        List<OrderItemUnit> units = new ArrayList<>();
        for (OrderItem item : DocumentModels.sortedItems(order)) {
            item.getUnits().stream()
                .sorted(Comparator.comparing(OrderItemUnit::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(units::add);
        }
        return units;
    }

    private Order readable(Long orderId, Authentication authentication, String language) {
        Order order = orderId == null ? null : orders.findForDocuments(orderId).orElse(null);
        if (order == null || !mayRead(order, authentication)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, DocumentText.text(language, "error.notFound"));
        }
        return order;
    }

    private boolean mayRead(Order order, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean staff = authentication.getAuthorities().stream()
            .anyMatch(authority -> PermissionConstants.OrderManagement.ORDER_VIEW_ALL.equals(authority.getAuthority()));
        if (staff) {
            return true;
        }
        boolean customer = authentication.getAuthorities().stream()
            .anyMatch(authority -> PermissionConstants.Customer.ORDER_VIEW_OWN.equals(authority.getAuthority()));
        User owner = order.getUser();
        if (!customer || owner == null || authentication.getName() == null) {
            return false;
        }
        // the account is found by the name in the token (user name, or email as the order service also accepts), so a renamed account is still itself
        return users.findByUsername(authentication.getName()).or(() -> users.findByEmail(authentication.getName()))
            .map(current -> current.getId().equals(owner.getId()))
            .orElse(false);
    }

    private static ResponseStatusException conflict(String language, String key) {
        return new ResponseStatusException(HttpStatus.CONFLICT, DocumentText.text(language, key));
    }
}
