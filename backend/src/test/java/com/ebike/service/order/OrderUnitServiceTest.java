package com.ebike.service.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.OrderUnitsRequest;
import com.ebike.dto.order.request.OrderUnitsRequest.Entry;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.warranty.MaintenanceReminderRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.admin.AuditLogService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V18: every vehicle of an order gets a frame number (unique) and an engine number before it is handed over. */
@ExtendWith(MockitoExtension.class)
class OrderUnitServiceTest {
    @Mock OrderRepository orders;
    @Mock OrderItemUnitRepository unitRepository;
    @Mock AuditLogService auditLog;
    @Mock WarrantyClaimRepository warrantyClaims;
    @Mock MaintenanceReminderRepository reminders;
    OrderUnitService service;
    Order order;
    OrderItem bikes;
    OrderItem accessory;

    private OrderItem line(long id, VehicleType type, int quantity) {
        Product product = new Product();
        ProductSpecification specification = new ProductSpecification();
        specification.setVehicleType(type);
        product.setSpecification(specification);
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setProduct(product);
        item.setProductName("Sản phẩm " + id);
        item.setQuantity(quantity);
        return item;
    }

    @BeforeEach void setup() {
        service = new OrderUnitService(orders, unitRepository, auditLog, warrantyClaims, reminders);
        bikes = line(1L, VehicleType.E_MOTORBIKE, 2);
        accessory = line(2L, VehicleType.ACCESSORY, 1);
        order = new Order();
        order.setId(10L);
        order.setOrderNumber("ORD-10");
        order.setStatus(OrderStatus.PROCESSING);
        order.getItems().add(bikes);
        order.getItems().add(accessory);
        org.mockito.Mockito.lenient().when(orders.findById(10L)).thenReturn(Optional.of(order));
        org.mockito.Mockito.lenient().when(orders.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void refused(OrderUnitsRequest request) {
        var ex = assertThrows(ResponseStatusException.class, () -> service.replaceUnits(10L, request));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(bikes.getUnits().isEmpty());
    }

    @Test void onlyVehiclesNeedNumbersAndAllOfThem() {
        assertTrue(OrderUnits.requiresNumbers(bikes));
        assertFalse(OrderUnits.requiresNumbers(accessory));
        assertFalse(OrderUnits.isComplete(order));
    }

    @Test void numbersAreCleanedUpAndSavedPerVehicle() {
        var saved = service.replaceUnits(10L, new OrderUnitsRequest(List.of(
            new Entry(1L, " rlh-abc 12345 ", "m1234-567"), new Entry(1L, "RLHABC12346", "M1234568"))));
        assertEquals(2, saved.size());
        assertEquals("RLH-ABC12345", saved.get(0).frameNumber());
        assertEquals("M1234-567", saved.get(0).engineNumber());
        assertTrue(OrderUnits.isComplete(order));
        verify(auditLog).record("nhập số khung, số máy", "ORD-10: 2 xe");
    }

    @Test void aPartialEntryIsKeptButTheOrderIsNotComplete() {
        service.replaceUnits(10L, new OrderUnitsRequest(List.of(new Entry(1L, "RLHABC12345", "M1234567"))));
        assertEquals(1, bikes.getUnits().size());
        assertFalse(OrderUnits.isComplete(order));
    }

    @Test void badNumbersAreRefused() {
        refused(new OrderUnitsRequest(List.of(new Entry(1L, "AB", "M1234567"))));
        refused(new OrderUnitsRequest(List.of(new Entry(1L, "RLHABC12345", ""))));
        refused(new OrderUnitsRequest(List.of(new Entry(1L, "RLH@BC12345", "M1234567"))));
    }

    @Test void theSameFrameTwiceOrTooManyVehiclesOrAnAccessoryIsRefused() {
        refused(new OrderUnitsRequest(List.of(new Entry(1L, "RLHABC12345", "M12345"), new Entry(1L, "rlhabc12345", "M23456"))));
        refused(new OrderUnitsRequest(List.of(new Entry(1L, "AAAAA11111", "M11111"), new Entry(1L, "BBBBB22222", "M22222"),
            new Entry(1L, "CCCCC33333", "M33333"))));
        refused(new OrderUnitsRequest(List.of(new Entry(2L, "RLHABC12345", "M1234567"))));
        refused(new OrderUnitsRequest(List.of(new Entry(99L, "RLHABC12345", "M1234567"))));
    }

    @Test void aFrameNumberAlreadyOnAnotherOrderIsRefused() {
        OrderItemUnit elsewhere = new OrderItemUnit();
        ReflectionTestUtils.setField(elsewhere, "id", 500L);
        elsewhere.setFrameNumber("RLHABC12345");
        when(unitRepository.findByFrameNumberIn(any())).thenReturn(List.of(elsewhere));
        refused(new OrderUnitsRequest(List.of(new Entry(1L, "RLHABC12345", "M1234567"))));
    }

    @Test void numbersCannotBeEnteredBeforeProcessing() {
        order.setStatus(OrderStatus.CONFIRMED);
        assertThrows(ResponseStatusException.class,
            () -> service.replaceUnits(10L, new OrderUnitsRequest(List.of(new Entry(1L, "RLHABC12345", "M1234567")))));
        verify(orders, never()).saveAndFlush(any());
    }

    private OrderItemUnit existing(long id, String frame, String engine) {
        OrderItemUnit unit = new OrderItemUnit();
        ReflectionTestUtils.setField(unit, "id", id);
        unit.setOrderItem(bikes);
        unit.setFrameNumber(frame);
        unit.setEngineNumber(engine);
        bikes.getUnits().add(unit);
        return unit;
    }

    @Test void savingAgainKeepsTheRowsSoWarrantyRecordsSurvive() {
        OrderItemUnit first = existing(7L, "RLHABC12345", "M1234567");
        OrderItemUnit second = existing(8L, "RLHABC12346", "M1234568");
        service.replaceUnits(10L, new OrderUnitsRequest(List.of(
            new Entry(1L, "RLHABC12345", "M9999999"), new Entry(1L, "RLHABC99999", "M1234568"))));
        assertEquals(2, bikes.getUnits().size());
        assertTrue(bikes.getUnits().contains(first));
        assertTrue(bikes.getUnits().contains(second));
        assertEquals("M9999999", first.getEngineNumber());
        assertEquals("RLHABC99999", second.getFrameNumber());
    }

    @Test void theWarrantyLengthIsPinnedOnceAndSurvivesALaterProductChange() {
        bikes.getProduct().getSpecification().setWarrantyMonths(24);
        service.replaceUnits(10L, new OrderUnitsRequest(List.of(new Entry(1L, "RLHABC12345", "M1234567"))));
        OrderItemUnit unit = bikes.getUnits().iterator().next();
        assertEquals(24, unit.getWarrantyMonths());

        bikes.getProduct().getSpecification().setWarrantyMonths(18);
        service.replaceUnits(10L, new OrderUnitsRequest(List.of(new Entry(1L, "RLHABC12345", "M7654321"))));
        assertEquals(24, unit.getWarrantyMonths());
    }

    @Test void theVehiclesAreLockedBeforeTheOrderIsRead() {
        service.replaceUnits(10L, new OrderUnitsRequest(List.of(new Entry(1L, "RLHABC12345", "M1234567"))));
        InOrder order = inOrder(unitRepository, orders);
        order.verify(unitRepository).findLockedByOrderId(10L);
        order.verify(orders).findById(10L);
    }

    @Test void aVehicleWithWarrantyRequestsCannotLoseItsNumbers() {
        existing(7L, "RLHABC12345", "M1234567");
        when(warrantyClaims.existsByUnitIdIn(any())).thenReturn(true);
        var ex = assertThrows(ResponseStatusException.class, () -> service.replaceUnits(10L, new OrderUnitsRequest(List.of())));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(1, bikes.getUnits().size());
    }

    @Test void aVehicleWithReminderCannotLoseItsNumbers() {
        existing(7L, "RLHABC12345", "M1234567");
        when(reminders.existsByUnitIdIn(any())).thenReturn(true);
        var ex = assertThrows(ResponseStatusException.class, () -> service.replaceUnits(10L, new OrderUnitsRequest(List.of())));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(1, bikes.getUnits().size());
        verify(orders, never()).saveAndFlush(any());
    }

    @Test void changingTheFrameOfAVehicleWithWarrantyHistoryIsRefused() {
        existing(7L, "RLHABC12345", "M1234567");
        when(warrantyClaims.existsByUnitIdIn(any())).thenReturn(true);
        var ex = assertThrows(ResponseStatusException.class, () -> service.replaceUnits(10L,
            new OrderUnitsRequest(List.of(new Entry(1L, "RLHABC99999", "M1234567")))));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals("RLHABC12345", bikes.getUnits().iterator().next().getFrameNumber());
    }

    @Test void movingAFrameToAnotherLineWithWarrantyHistoryIsRefused() {
        OrderItem otherBikes = line(3L, VehicleType.E_MOTORBIKE, 1);
        order.getItems().add(otherBikes);
        existing(7L, "RLHABC12345", "M1234567");
        when(reminders.existsByUnitIdIn(any())).thenReturn(true);
        var ex = assertThrows(ResponseStatusException.class, () -> service.replaceUnits(10L,
            new OrderUnitsRequest(List.of(new Entry(3L, "RLHABC12345", "M1234567")))));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(1, bikes.getUnits().size());
        assertTrue(otherBikes.getUnits().isEmpty());
    }

    @Test void swappingNumbersAcrossLinesNeverInsertsBeforeTheOldRowIsGone() {
        OrderItem otherBikes = line(3L, VehicleType.E_MOTORBIKE, 1);
        order.getItems().add(otherBikes);
        existing(7L, "RLHXXXX1", "M1234567");
        OrderItemUnit z = new OrderItemUnit();
        ReflectionTestUtils.setField(z, "id", 8L);
        z.setOrderItem(otherBikes);
        z.setFrameNumber("RLHZZZZ1");
        z.setEngineNumber("M7654321");
        otherBikes.getUnits().add(z);
        // line 1 changes X to W while line 3 takes X: the row that carries X is deleted and flushed, never reused for W
        service.replaceUnits(10L, new OrderUnitsRequest(List.of(
            new Entry(1L, "RLHWWWW1", "M1234567"), new Entry(3L, "RLHXXXX1", "M7654321"))));
        OrderItemUnit onFirst = bikes.getUnits().iterator().next();
        assertEquals("RLHWWWW1", onFirst.getFrameNumber());
        assertEquals(null, onFirst.getId());
        assertEquals("RLHXXXX1", otherBikes.getUnits().iterator().next().getFrameNumber());
        verify(orders, org.mockito.Mockito.times(2)).saveAndFlush(any());
    }

    @Test void movingAFrameToAnotherLineWithoutHistoryDeletesThenInserts() {
        OrderItem otherBikes = line(3L, VehicleType.E_MOTORBIKE, 1);
        order.getItems().add(otherBikes);
        existing(7L, "RLHABC12345", "M1234567");
        service.replaceUnits(10L, new OrderUnitsRequest(List.of(new Entry(3L, "RLHABC12345", "M1234567"))));
        assertTrue(bikes.getUnits().isEmpty());
        assertEquals(1, otherBikes.getUnits().size());
        assertEquals("RLHABC12345", otherBikes.getUnits().iterator().next().getFrameNumber());
        // one flush for the delete, one for the insert, so the unique frame number is free in between
        verify(orders, org.mockito.Mockito.times(2)).saveAndFlush(any());
    }
}
