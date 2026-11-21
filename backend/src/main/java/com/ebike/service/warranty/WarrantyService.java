package com.ebike.service.warranty;

import com.ebike.dto.warranty.WarrantyClaimResponse;
import com.ebike.dto.warranty.WarrantyClaimUpdateRequest;
import com.ebike.dto.warranty.WarrantyLookupResponse;
import com.ebike.dto.warranty.WarrantyUnitResponse;
import com.ebike.event.notification.WarrantyClaimStatusChangedEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.warranty.WarrantyClaim;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.admin.AuditLogService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * V20: the electronic warranty book. A vehicle's warranty is computed when it is read (delivery time plus the months on the
 * product), so no date is stored; only the customers' requests are.
 */
@Service
public class WarrantyService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 50;
    private static final Pattern FRAME = Pattern.compile("^[A-Z0-9-]{5,30}$");
    private static final Set<String> FILTERS = Set.of(
        WarrantyClaim.NEW, WarrantyClaim.RECEIVED, WarrantyClaim.REPAIRING, WarrantyClaim.DONE, WarrantyClaim.REJECTED);
    /** A bike has at most one request that is not finished; a new one is taken only after the staff closed the last. */
    private static final Set<String> OPEN = Set.of(WarrantyClaim.NEW, WarrantyClaim.RECEIVED, WarrantyClaim.REPAIRING);

    private final OrderItemUnitRepository units;
    private final WarrantyClaimRepository claims;
    private final UserRepository users;
    private final AuditLogService auditLog;
    private final List<Period> intervals;
    private final Clock clock;

    @Autowired
    public WarrantyService(
        OrderItemUnitRepository units,
        WarrantyClaimRepository claims,
        UserRepository users,
        AuditLogService auditLog,
        @Value("${app.maintenance.intervals:" + WarrantyRules.DEFAULT_INTERVALS + "}") String intervals
    ) {
        this(units, claims, users, auditLog, intervals, Clock.systemDefaultZone());
    }

    WarrantyService(
        OrderItemUnitRepository units,
        WarrantyClaimRepository claims,
        UserRepository users,
        AuditLogService auditLog,
        String intervals,
        Clock clock
    ) {
        this.units = units;
        this.claims = claims;
        this.users = users;
        this.auditLog = auditLog;
        this.intervals = WarrantyRules.parseIntervals(intervals);
        this.clock = clock;
    }

    /** The signed-in customer's delivered vehicles, newest first. */
    @Transactional(readOnly = true)
    public List<WarrantyUnitResponse> book(String username) {
        requireUsername(username);
        List<OrderItemUnit> mine = units.findDeliveredByUsername(username);
        Map<Long, String> latestClaim = new HashMap<>();
        if (!mine.isEmpty()) {
            // newest request wins: the list is read oldest to newest, so a later row overwrites an earlier one
            claims.findByUnitIdIn(mine.stream().map(OrderItemUnit::getId).toList()).stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .forEach(claim -> latestClaim.put(claim.getUnit().getId(), claim.getStatus()));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return mine.stream().map(unit -> toUnitResponse(unit, now, latestClaim.get(unit.getId()))).toList();
    }

    /** Public lookup by frame number. A missing frame and a typo give the same answer, so numbers cannot be probed one by one. */
    @Transactional(readOnly = true)
    public WarrantyLookupResponse lookup(String frame) {
        String normalized = normalizeFrame(frame);
        if (!FRAME.matcher(normalized).matches()) {
            throw bad("Số khung gồm 5 đến 30 ký tự chữ, số hoặc dấu gạch ngang.", "A frame number has 5 to 30 letters, digits or hyphens.");
        }
        OrderItemUnit unit = units.findForWarrantyByFrameNumber(normalized)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, WarrantyMessages.text("Không tìm thấy xe có số khung này.", "No vehicle with this frame number was found.")));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime starts = startsAt(unit);
        if (starts == null) {
            // not handed over yet: answer like an unknown frame, so the page cannot be used to probe frames of open orders
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, WarrantyMessages.text("Không tìm thấy xe có số khung này.", "No vehicle with this frame number was found."));
        }
        Integer months = warrantyMonths(unit);
        return new WarrantyLookupResponse(
            unit.getFrameNumber(),
            unit.getOrderItem().getProductName(),
            colourOf(unit.getOrderItem()),
            months,
            date(starts),
            date(WarrantyRules.endsAt(starts, months)),
            WarrantyRules.coverage(starts, months, now));
    }

    @Transactional(readOnly = true)
    public List<WarrantyClaimResponse> claimsOf(String username) {
        requireUsername(username);
        return claims.findByUserUsernameOrderByCreatedAtDesc(username).stream().map(this::toClaimResponse).toList();
    }

    /** A customer asks for warranty service on one of their delivered vehicles. After the warranty ended the request is still taken, marked as a paid repair. */
    @Transactional
    public WarrantyClaimResponse createClaim(String username, Long unitId, String description) {
        requireUsername(username);
        if (unitId == null) {
            throw bad("Vui lòng chọn xe cần bảo hành.", "Please choose the vehicle to claim for.");
        }
        String text = description == null ? "" : description.trim();
        if (text.length() < 10 || text.length() > 2000) {
            throw bad("Mô tả lỗi cần từ 10 đến 2000 ký tự.", "Describe the problem in 10 to 2000 characters.");
        }
        // The lock is the first statement of the transaction on purpose: under REPEATABLE READ the first plain SELECT fixes the
        // snapshot, and a snapshot taken before the lock would not show a request that another transaction committed while we waited.
        units.findLockedByIdAndOwner(unitId, username)
            .orElseThrow(WarrantyService::unitNotFound);
        User user = users.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required"));
        OrderItemUnit unit = units.findForWarrantyById(unitId)
            .filter(found -> ownedBy(found, username))
            .orElseThrow(WarrantyService::unitNotFound);
        if (unit.getOrderItem().getOrder().getStatus() != OrderStatus.DELIVERED) {
            throw bad("Xe chỉ được yêu cầu bảo hành sau khi đã giao.", "A warranty request can be made only after the vehicle was delivered.");
        }
        if (claims.existsByUnitIdAndStatusIn(unit.getId(), OPEN)) {
            throw openRequestExists();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        String coverage = WarrantyRules.coverage(startsAt(unit), warrantyMonths(unit), now);

        WarrantyClaim claim = new WarrantyClaim();
        claim.setUnit(unit);
        claim.setUser(user);
        claim.setDescription(text);
        claim.setOutOfWarranty(WarrantyRules.EXPIRED.equals(coverage));
        try {
            return toClaimResponse(claims.save(claim));
        } catch (DataIntegrityViolationException exception) {
            // the database allows one open request per vehicle (uk_warranty_claims_open_unit): a second one that slipped past the check above
            throw openRequestExists();
        }
    }

    /** The staff queue, newest first, one page at a time ({@code size} is held between 1 and {@link #MAX_PAGE_SIZE}). */
    @Transactional(readOnly = true)
    public List<WarrantyClaimResponse> listClaims(String status, int page, int size) {
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (filter != null && !FILTERS.contains(filter)) {
            throw bad("Trạng thái không hợp lệ.", "Invalid status.");
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return (filter == null ? claims.findAllBy(pageable) : claims.findByStatus(filter, pageable))
            .stream().map(this::toClaimResponse).toList();
    }

    @Transactional
    public WarrantyClaimResponse updateClaim(Long id, WarrantyClaimUpdateRequest request) {
        WarrantyClaim claim = claims.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, WarrantyMessages.text("Không tìm thấy yêu cầu bảo hành.", "Warranty request not found.")));
        String previous = claim.getStatus();
        String previousNote = claim.getStaffNote();
        boolean closed = WarrantyClaim.DONE.equals(previous) || WarrantyClaim.REJECTED.equals(previous);
        if (request != null && request.status() != null && !request.status().isBlank()) {
            String next = request.status().trim().toUpperCase(Locale.ROOT);
            if (!WarrantyRules.isKnownStatus(next)) {
                throw bad("Trạng thái không hợp lệ.", "Invalid status.");
            }
            if (!WarrantyRules.canMove(previous, next)) {
                throw bad("Không thể chuyển yêu cầu từ bước hiện tại sang bước này.", "The request cannot move from its current step to this one.");
            }
            claim.setStatus(next);
        }
        if (request != null && request.staffNote() != null) {
            if (closed) {
                throw bad("Yêu cầu đã đóng, không thể sửa ghi chú.", "This request is closed; its note can no longer be edited.");
            }
            String note = request.staffNote().trim();
            if (note.length() > 3000) {
                throw bad("Ghi chú tối đa 3000 ký tự.", "A note can have at most 3000 characters.");
            }
            claim.setStaffNote(note.isEmpty() ? null : note);
        }
        if (WarrantyClaim.REJECTED.equals(claim.getStatus()) && !WarrantyClaim.REJECTED.equals(previous) && (claim.getStaffNote() == null || claim.getStaffNote().isBlank())) {
            throw bad("Vui lòng ghi lý do khi từ chối yêu cầu.", "Please give a reason when rejecting a request.");
        }
        boolean changed = !previous.equals(claim.getStatus()) || !java.util.Objects.equals(previousNote, claim.getStaffNote());
        WarrantyClaim saved = claims.save(claim);
        if (changed) {
            auditLog.record("xử lý yêu cầu bảo hành", "#" + saved.getId() + ": " + previous + " → " + saved.getStatus());
        }
        if (!previous.equals(saved.getStatus())) {
            OrderItemUnit unit = saved.getUnit();
            NotificationEventBridge.publish(new WarrantyClaimStatusChangedEvent(
                saved.getUser().getId(), unit.getOrderItem().getProductName(), unit.getFrameNumber(), saved.getStatus()));
        }
        return toClaimResponse(saved);
    }

    private WarrantyUnitResponse toUnitResponse(OrderItemUnit unit, LocalDateTime now, String claimStatus) {
        OrderItem item = unit.getOrderItem();
        Order order = item.getOrder();
        LocalDateTime starts = startsAt(unit);
        Integer months = warrantyMonths(unit);
        return new WarrantyUnitResponse(
            unit.getId(),
            order.getId(),
            order.getOrderNumber(),
            item.getProductName(),
            item.getProduct().getSlug(),
            colourOf(item),
            unit.getFrameNumber(),
            unit.getEngineNumber(),
            months,
            date(starts),
            date(WarrantyRules.endsAt(starts, months)),
            WarrantyRules.coverage(starts, months, now),
            date(WarrantyRules.nextMaintenanceAt(starts, intervals, now)),
            claimStatus);
    }

    private WarrantyClaimResponse toClaimResponse(WarrantyClaim claim) {
        OrderItemUnit unit = claim.getUnit();
        User user = claim.getUser();
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return new WarrantyClaimResponse(
            claim.getId(),
            unit.getId(),
            name.isEmpty() ? user.getEmail() : name,
            user.getEmail(),
            unit.getOrderItem().getProductName(),
            colourOf(unit.getOrderItem()),
            unit.getFrameNumber(),
            claim.getDescription(),
            claim.getStatus(),
            claim.isOutOfWarranty(),
            date(WarrantyRules.endsAt(startsAt(unit), warrantyMonths(unit))),
            claim.getStaffNote(),
            claim.getCreatedAt(),
            claim.getUpdatedAt());
    }

    static LocalDateTime startsAt(OrderItemUnit unit) {
        Order order = unit.getOrderItem().getOrder();
        if (order.getStatus() != OrderStatus.DELIVERED) {
            return null;
        }
        LocalDateTime shipped = order.getShipment() == null ? null : order.getShipment().getDeliveredAt();
        // orders.delivered_at is fixed when the order became DELIVERED; updated_at moves on every save, so it is the last resort
        return WarrantyRules.startsAt(shipped != null ? shipped : order.getDeliveredAt(), order.getUpdatedAt());
    }

    static Integer warrantyMonths(OrderItemUnit unit) {
        var product = unit.getOrderItem().getProduct();
        return product == null || product.getSpecification() == null ? null : product.getSpecification().getWarrantyMonths();
    }

    static String colourOf(OrderItem item) {
        var variant = item.getProductVariant();
        if (variant == null) {
            return null;
        }
        return variant.getColorName() != null ? variant.getColorName() : variant.getVariantName();
    }

    private static boolean ownedBy(OrderItemUnit unit, String username) {
        User owner = unit.getOrderItem().getOrder().getUser();
        return owner != null && username.equals(owner.getUsername());
    }

    private static LocalDate date(LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    static String normalizeFrame(String frame) {
        return frame == null ? "" : frame.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private void requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
    }

    private static ResponseStatusException openRequestExists() {
        return new ResponseStatusException(HttpStatus.CONFLICT, WarrantyMessages.text("Xe này đang có một yêu cầu bảo hành chưa xử lý xong.", "This vehicle already has an open warranty request."));
    }

    private static ResponseStatusException unitNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, WarrantyMessages.text("Không tìm thấy xe này trong tài khoản của bạn.", "This vehicle was not found in your account."));
    }

    private ResponseStatusException bad(String vietnamese, String english) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, WarrantyMessages.text(vietnamese, english));
    }
}
