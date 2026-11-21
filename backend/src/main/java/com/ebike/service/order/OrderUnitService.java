package com.ebike.service.order;

import com.ebike.dto.order.request.OrderUnitsRequest;
import com.ebike.dto.order.response.OrderItemUnitResponse;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.warranty.MaintenanceReminderRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.admin.AuditLogService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Staff record the frame and engine number of each vehicle of an order before handing it over (V18). */
@Service
public class OrderUnitService {
    // Letters, digits and hyphens: real frame (VIN-style) and motor numbers look like RLHPC1234MY123456 or 6D12-345678.
    private static final Pattern NUMBER = Pattern.compile("[A-Z0-9-]{5,30}");
    private static final Set<OrderStatus> EDITABLE = Set.of(
        OrderStatus.PROCESSING, OrderStatus.READY_FOR_PICKUP, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orders;
    private final OrderItemUnitRepository unitRepository;
    private final AuditLogService auditLog;
    private final WarrantyClaimRepository warrantyClaims;
    private final MaintenanceReminderRepository maintenanceReminders;

    public OrderUnitService(OrderRepository orders, OrderItemUnitRepository unitRepository, AuditLogService auditLog,
                            WarrantyClaimRepository warrantyClaims, MaintenanceReminderRepository maintenanceReminders) {
        this.orders = orders;
        this.unitRepository = unitRepository;
        this.warrantyClaims = warrantyClaims;
        this.maintenanceReminders = maintenanceReminders;
        this.auditLog = auditLog;
    }

    /**
     * Replaces the numbers of the order with the ones given. An order may be saved with only some of its vehicles
     * filled in; handing it over (Đã giao / Đã nhận) needs all of them.
     */
    @Transactional
    public List<OrderItemUnitResponse> replaceUnits(Long orderId, OrderUnitsRequest request) {
        Order order = orders.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!EDITABLE.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Chỉ nhập số khung, số máy khi đơn đã sang bước đang xử lý.");
        }
        List<OrderUnitsRequest.Entry> entries = request == null || request.units() == null ? List.of() : request.units();

        Map<Long, OrderItem> items = new HashMap<>();
        order.getItems().forEach(item -> items.put(item.getId(), item));
        Map<Long, List<OrderUnitsRequest.Entry>> perItem = new HashMap<>();
        Set<String> frames = new HashSet<>();
        List<NormalizedEntry> normalized = new ArrayList<>();
        for (OrderUnitsRequest.Entry entry : entries) {
            OrderItem item = entry.orderItemId() == null ? null : items.get(entry.orderItemId());
            if (item == null || !OrderUnits.requiresNumbers(item)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dòng sản phẩm không hợp lệ hoặc không cần số khung.");
            }
            String frame = clean(entry.frameNumber());
            String engine = clean(entry.engineNumber());
            if (!NUMBER.matcher(frame).matches() || !NUMBER.matcher(engine).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số khung và số máy gồm 5 đến 30 ký tự chữ, số hoặc dấu gạch ngang.");
            }
            if (!frames.add(frame)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số khung " + frame + " bị nhập hai lần.");
            }
            perItem.computeIfAbsent(item.getId(), id -> new ArrayList<>()).add(entry);
            normalized.add(new NormalizedEntry(item, frame, engine));
        }
        perItem.forEach((itemId, list) -> {
            if (list.size() > items.get(itemId).getQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dòng " + items.get(itemId).getProductName() + " chỉ có " + items.get(itemId).getQuantity() + " xe.");
            }
        });
        // A frame number belongs to one vehicle: refuse one already recorded on another order line.
        Set<Long> mine = new HashSet<>();
        order.getItems().forEach(item -> item.getUnits().forEach(unit -> mine.add(unit.getId())));
        for (OrderItemUnit other : unitRepository.findByFrameNumberIn(frames)) {
            if (!mine.contains(other.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số khung " + other.getFrameNumber() + " đã được ghi cho một đơn hàng khác.");
            }
        }

        // Keep the rows that already exist (warranty requests and maintenance reminders hang off their ids): a vehicle
        // whose frame number stays on the same order line is updated in place. A number moved to another line counts as
        // a dropped row plus a new one, so it meets the same 409 check (orphanRemoval would delete the row and, with it,
        // the warranty history, even though the frame number reappears on the other line).
        Map<String, OrderItemUnit> byFrame = new HashMap<>();
        order.getItems().forEach(item -> item.getUnits().forEach(unit -> byFrame.put(unit.getFrameNumber(), unit)));
        Set<OrderItemUnit> kept = new HashSet<>();
        List<NormalizedEntry> unmatched = new ArrayList<>();
        for (NormalizedEntry entry : normalized) {
            OrderItemUnit unit = byFrame.get(entry.frame());
            if (unit == null || unit.getOrderItem() != entry.item()) {
                unmatched.add(entry);
                continue;
            }
            unit.setEngineNumber(entry.engine());
            kept.add(unit);
        }
        List<OrderItemUnit> spare = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            for (OrderItemUnit unit : item.getUnits()) {
                if (!kept.contains(unit)) {
                    spare.add(unit);
                }
            }
        }
        // A changed number reuses a spare row of the same line (a typo fix before anything hangs off the row).
        List<OrderItemUnit> reused = new ArrayList<>();
        List<NormalizedEntry> reuseEntries = new ArrayList<>();
        List<NormalizedEntry> created = new ArrayList<>();
        List<OrderItemUnit> removed = new ArrayList<>(spare);
        for (NormalizedEntry entry : unmatched) {
            OrderItemUnit reuse = removed.stream().filter(unit -> unit.getOrderItem() == entry.item()
                && !byFrame.containsKey(entry.frame())).findFirst().orElse(null);
            if (reuse == null) {
                created.add(entry);
            } else {
                removed.remove(reuse);
                reused.add(reuse);
                reuseEntries.add(entry);
            }
        }
        // The frame number of a vehicle that has a warranty request or a service reminder is its identity: no edit, no delete.
        List<Long> touchedIds = spare.stream().map(OrderItemUnit::getId).filter(Objects::nonNull).toList();
        // Lock the rows first: a warranty request being filed at this moment waits for us instead of being cascaded away.
        touchedIds.forEach(unitRepository::findLockedById);
        if (!touchedIds.isEmpty() && (!warrantyClaims.findByUnitIdIn(touchedIds).isEmpty()
            || !maintenanceReminders.findByUnitIdIn(touchedIds).isEmpty())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Xe này đã có yêu cầu bảo hành hoặc mốc nhắc bảo dưỡng, không thể đổi hoặc xóa số khung của xe.");
        }
        // Delete first and flush, so a number that moved to another line is free again before it is inserted.
        if (!removed.isEmpty()) {
            removed.forEach(unit -> unit.getOrderItem().getUnits().remove(unit));
            orders.saveAndFlush(order);
        }
        for (int i = 0; i < reused.size(); i++) {
            reused.get(i).setFrameNumber(reuseEntries.get(i).frame());
            reused.get(i).setEngineNumber(reuseEntries.get(i).engine());
        }
        for (NormalizedEntry entry : created) {
            OrderItemUnit unit = new OrderItemUnit();
            unit.setOrderItem(entry.item());
            unit.setFrameNumber(entry.frame());
            unit.setEngineNumber(entry.engine());
            entry.item().getUnits().add(unit);
        }
        orders.saveAndFlush(order);
        auditLog.record("nhập số khung, số máy", order.getOrderNumber() + ": " + normalized.size() + " xe");

        return order.getItems().stream()
            .flatMap(item -> item.getUnits().stream())
            .map(unit -> new OrderItemUnitResponse(unit.getId(), unit.getFrameNumber(), unit.getEngineNumber()))
            .toList();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record NormalizedEntry(OrderItem item, String frame, String engine) {
    }
}
