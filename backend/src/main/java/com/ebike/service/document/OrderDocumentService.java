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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
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
import org.springframework.transaction.annotation.Transactional;
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
    private final Clock clock;

    public OrderDocumentService(
        OrderRepository orders,
        InstallmentApplicationRepository installments,
        UserRepository users,
        UserPreferenceRepository preferences,
        StoreProfile store,
        PdfRenderer renderer,
        @Value("${app.maintenance.intervals:" + WarrantyRules.DEFAULT_INTERVALS + "}") String intervals,
        @Value("${app.time-zone:Asia/Ho_Chi_Minh}") String timeZone
    ) {
        this.orders = orders;
        this.installments = installments;
        this.users = users;
        this.preferences = preferences;
        this.store = store;
        this.renderer = renderer;
        this.intervals = WarrantyRules.parseIntervals(intervals);
        this.clock = Clock.system(ZoneId.of(timeZone));
    }

    /** The sales receipt of an order that is not cancelled. Before the bike is handed over it is marked "provisional". */
    @Transactional(readOnly = true)
    public PdfFile receipt(Long orderId, Authentication authentication, String language) {
        Order order = readable(orderId, authentication, language);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw conflict(language, "error.cancelled");
        }
        return buildReceipt(order, language);
    }

    /** The warranty card of the vehicles of a delivered order, one page each; {@code unitId} narrows it to one vehicle. */
    @Transactional(readOnly = true)
    public PdfFile warrantyCard(Long orderId, Long unitId, Authentication authentication, String language) {
        Order order = readable(orderId, authentication, language);
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw conflict(language, "error.notDelivered");
        }
        List<OrderItemUnit> units = unitsOf(order);
        if (unitId != null) {
            units = units.stream().filter(unit -> unitId.equals(unit.getId())).toList();
            if (units.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, DocumentText.text(language, "error.unitNotFound"));
            }
        }
        if (units.isEmpty()) {
            throw conflict(language, "error.noNumbers");
        }
        return buildCard(order, units, language, unitId == null ? null : units.get(0).getFrameNumber());
    }

    /**
     * What to email when an order was handed over: the receipt, plus the warranty cards of the vehicles that have numbers. Empty when there is
     * nobody to write to (no address), or the account was erased since; the caller must not fail the order because of it.
     */
    @Transactional(readOnly = true)
    public Optional<DeliveryDocuments> forDeliveredOrder(Long orderId) {
        Order order = orders.findById(orderId).orElse(null);
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
        // a guest has no saved language: Vietnamese, the shop's base language
        String language = user == null ? "vi" : preferences.findByUserId(user.getId()).map(saved -> DocumentFormat.language(saved.getLanguage())).orElse("vi");
        List<PdfFile> files = new ArrayList<>();
        files.add(buildReceipt(order, language));
        List<OrderItemUnit> units = unitsOf(order);
        if (!units.isEmpty()) {
            files.add(buildCard(order, units, language, null));
        }
        String name = DocumentModels.customerName(order, language);
        return Optional.of(new DeliveryDocuments(email, "—".equals(name) ? DocumentText.text(language, "mail.guestName") : name, order.getOrderNumber(), language, files, user == null));
    }

    private PdfFile buildReceipt(Order order, String language) {
        var installment = installments.findByOrderId(order.getId()).orElse(null);
        var model = DocumentModels.receipt(order, installment, store, language, LocalDateTime.now(clock));
        return new PdfFile("receipt-" + PdfFile.safeName(order.getOrderNumber()) + ".pdf", render("receipt", model, language));
    }

    private PdfFile buildCard(Order order, List<OrderItemUnit> units, String language, String singleFrame) {
        var model = DocumentModels.warrantyCards(order, units, store, intervals, language, LocalDateTime.now(clock));
        String name = singleFrame == null ? PdfFile.safeName(order.getOrderNumber()) : PdfFile.safeName(singleFrame);
        return new PdfFile("warranty-card-" + name + ".pdf", render("warranty-card", model, language));
    }

    private byte[] render(String template, java.util.Map<String, Object> model, String language) {
        try {
            return renderer.render(template, model);
        } catch (RuntimeException exception) {
            log.error("Could not render {} for order {}", template, model.get("orderNumber"), exception);
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
        Order order = orderId == null ? null : orders.findById(orderId).orElse(null);
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
