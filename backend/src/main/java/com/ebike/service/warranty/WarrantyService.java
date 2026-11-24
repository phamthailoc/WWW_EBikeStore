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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * V20: the electronic warranty book. A vehicle's warranty end is computed when it is read, from the hand-over time kept on the
 * order ({@code orders.delivered_at}) and the months pinned on the vehicle row when its numbers were recorded; the customers'
 * requests are stored.
 */
@Service
public class WarrantyService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 50;
    /** The status filter that lists every request, open or closed. */
    public static final String ALL = "ALL";
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
                // created_at is stored to the second, so the id breaks a tie: the later row of the same second is the newer request
                .sorted(Comparator.comparing(WarrantyClaim::getCreatedAt).thenComparing(WarrantyClaim::getId))
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
            throw bad("Số khung gồm 5 đến 30 ký tự chữ, số hoặc dấu gạch ngang.");
        }
        OrderItemUnit unit = units.findForWarrantyByFrameNumber(normalized)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy xe có số khung này."));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime starts = startsAt(unit);
        if (starts == null) {
            // not handed over yet: answer like an unknown frame, so the page cannot be used to probe frames of open orders
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy xe có số khung này.");
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
        return claims.findByUserUsernameOrderByCreatedAtDescIdDesc(username).stream().map(this::toClaimResponse).toList();
    }

    /** A customer asks for warranty service on one of their delivered vehicles. After the warranty ended the request is still taken, marked as a paid repair. */
    @Transactional
    public WarrantyClaimResponse createClaim(String username, Long unitId, String description) {
        requireUsername(username);
        if (unitId == null) {
            throw bad("Vui lòng chọn xe cần bảo hành.");
        }
        String text = description == null ? "" : description.trim();
        if (text.length() < 10 || text.length() > 2000) {
            throw bad("Mô tả lỗi cần từ 10 đến 2000 ký tự.");
        }
        // The lock is the first statement of the transaction on purpose: under REPEATABLE READ the first plain SELECT fixes the
        // snapshot, and a snapshot taken before the lock would not show a request that another transaction committed while we waited.
        units.findLockedById(unitId)
            .orElseThrow(WarrantyService::unitNotFound);
        User user = users.findByUsername(username)
            .orElseThrow(WarrantyService::signInRequired);
        OrderItemUnit unit = units.findForWarrantyById(unitId)
            .filter(found -> ownedBy(found, username))
            .orElseThrow(WarrantyService::unitNotFound);
        if (unit.getOrderItem().getOrder().getStatus() != OrderStatus.DELIVERED) {
            throw bad("Xe chỉ được yêu cầu bảo hành sau khi đã giao.");
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
            String detail = String.valueOf(exception.getMostSpecificCause().getMessage());
            if (!detail.contains("uk_warranty_claims_open_unit") && !detail.contains("open_unit_id")) {
                throw exception; // any other constraint is a real fault, not a duplicate request
            }
            throw openRequestExists();
        }
    }

    /** One page of the staff queue and whether another page follows it. */
    public record ClaimPage(List<WarrantyClaimResponse> items, boolean hasNext) {
    }

    /**
     * The staff queue, one page at a time ({@code size} is held between 1 and {@link #MAX_PAGE_SIZE}). With no status it holds the requests
     * still open (NEW, RECEIVED, REPAIRING), oldest first, so the longest-waiting one is on top and closed ones do not push it away;
     * a single open status is listed oldest first too, while {@code ALL} (everything) and a closed status (DONE, REJECTED) are listed newest first.
     */
    @Transactional(readOnly = true)
    public ClaimPage listClaims(String status, int page, int size) {
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (filter != null && !ALL.equals(filter) && !FILTERS.contains(filter)) {
            throw bad("Trạng thái không hợp lệ.");
        }
        boolean openQueue = filter == null || OPEN.contains(filter);
        Sort sort = openQueue ? Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")) : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
        Slice<WarrantyClaim> found;
        if (filter == null) {
            found = claims.findByStatusIn(OPEN, pageable);
        } else if (ALL.equals(filter)) {
            found = claims.findAllBy(pageable);
        } else {
            found = claims.findByStatus(filter, pageable);
        }
        return new ClaimPage(found.stream().map(this::toClaimResponse).toList(), found.hasNext());
    }


    @Transactional
    public WarrantyClaimResponse updateClaim(Long id, WarrantyClaimUpdateRequest request) {
        WarrantyClaim claim = claims.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu bảo hành."));
        String previous = claim.getStatus();
        String previousNote = claim.getStaffNote();
        boolean closed = WarrantyClaim.DONE.equals(previous) || WarrantyClaim.REJECTED.equals(previous);
        if (request != null && request.status() != null && !request.status().isBlank()) {
            String next = request.status().trim().toUpperCase(Locale.ROOT);
            if (!WarrantyRules.isKnownStatus(next)) {
                throw bad("Trạng thái không hợp lệ.");
            }
            if (!WarrantyRules.canMove(previous, next)) {
                throw bad("Không thể chuyển yêu cầu từ bước hiện tại sang bước này.");
            }
            claim.setStatus(next);
        }
        if (request != null && request.staffNote() != null) {
            if (closed) {
                throw bad("Yêu cầu đã đóng, không thể sửa ghi chú.");
            }
            String note = request.staffNote().trim();
            if (note.length() > 3000) {
                throw bad("Ghi chú tối đa 3000 ký tự.");
            }
            claim.setStaffNote(note.isEmpty() ? null : note);
        }
        if (WarrantyClaim.REJECTED.equals(claim.getStatus()) && !WarrantyClaim.REJECTED.equals(previous) && (claim.getStaffNote() == null || claim.getStaffNote().isBlank() || Objects.equals(previousNote, claim.getStaffNote()))) {
            // the reason must be written for the rejection: an earlier note ("please bring the bike in") would read as the reason
            throw bad("Vui lòng ghi lý do khi từ chối yêu cầu.");
        }
        boolean statusChanged = !previous.equals(claim.getStatus());
        boolean changed = statusChanged || !Objects.equals(previousNote, claim.getStaffNote());
        WarrantyClaim saved = claims.save(claim);
        if (changed) {
            auditLog.record("xử lý yêu cầu bảo hành", "#" + saved.getId() + (statusChanged
                ? ": " + previous + " → " + saved.getStatus()
                : ": cập nhật ghi chú (" + saved.getStatus() + ")"));
        }
        if (changed && (statusChanged || saved.getStaffNote() != null)) {
            // the note is what the customer reads in the warranty book, so a new note is told too (as the pseudo status NOTE when the step did not move)
            OrderItemUnit unit = saved.getUnit();
            NotificationEventBridge.publish(new WarrantyClaimStatusChangedEvent(
                saved.getUser().getId(), unit.getOrderItem().getProductName(), unit.getFrameNumber(),
                statusChanged ? saved.getStatus() : WarrantyMessages.NOTE_UPDATED));
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
            claim.getUpdatedAt(),
            unit.getOrderItem().getOrder().getId());
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

    public static Integer warrantyMonths(OrderItemUnit unit) {
        if (unit.getWarrantyMonths() != null) {
            return unit.getWarrantyMonths();
        }
        // not pinned (the product had no length when the numbers were recorded): fall back to the product's current length
        var product = unit.getOrderItem().getProduct();
        return product == null || product.getSpecification() == null ? null : product.getSpecification().getWarrantyMonths();
    }

    public static String colourOf(OrderItem item) {
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
            throw signInRequired();
        }
    }

    private static ResponseStatusException openRequestExists() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Xe này đang có một yêu cầu bảo hành chưa xử lý xong.");
    }

    private static ResponseStatusException signInRequired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để tiếp tục.");
    }

    private static ResponseStatusException unitNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy xe này trong tài khoản của bạn.");
    }

    private ResponseStatusException bad(String vietnamese) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, vietnamese);
    }
}
