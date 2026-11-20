package com.ebike.service.order;

import com.ebike.dto.order.request.OrderUnitsRequest;
import com.ebike.dto.order.response.OrderItemUnitResponse;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.AuditLogService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public OrderUnitService(OrderRepository orders, OrderItemUnitRepository unitRepository, AuditLogService auditLog) {
        this.orders = orders;
        this.unitRepository = unitRepository;
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

        // Delete the old rows first and flush, so a frame number that stays the same does not hit the unique key.
        order.getItems().forEach(item -> item.getUnits().clear());
        orders.saveAndFlush(order);
        for (NormalizedEntry entry : normalized) {
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
