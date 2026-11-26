package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.OrderUnitsRequest;
import com.ebike.dto.order.response.OrderItemUnitResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.repository.order.OrderItemUnitRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.warranty.MaintenanceReminderRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.order.OrderUnitService;
import com.ebike.service.order.OrderUnits;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderUnitServiceTest {

    @Mock OrderRepository orders;
    @Mock OrderItemUnitRepository unitRepository;
    @Mock AuditLogService auditLog;
    @Mock WarrantyClaimRepository warrantyClaims;
    @Mock MaintenanceReminderRepository reminders;
    OrderUnitService service;

    Order order;
    OrderItem bikeLine;
    OrderItem helmetLine;

    @BeforeEach
    void setUp() {
        service = new OrderUnitService(orders, unitRepository, auditLog, warrantyClaims, reminders);
        User owner = OrderFixtures.user(1L, "alice", "a@x.com");
        order = OrderFixtures.order(10L, "ORD-10", OrderStatus.PROCESSING, owner);
        Product bike = OrderFixtures.bike(1L, "Bike", "100", 5, VehicleType.E_BIKE);
        Product helmet = OrderFixtures.accessory(2L, "10", 5);
        bikeLine = OrderFixtures.line(order, 100L, bike, null, 2, "100");
        helmetLine = OrderFixtures.line(order, 200L, helmet, null, 3, "10");
        when(orders.findById(10L)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(unitRepository.findByFrameNumberIn(anyCollection())).thenReturn(List.of());
    }

    private static OrderUnitsRequest request(OrderUnitsRequest.Entry... entries) {
        return new OrderUnitsRequest(List.of(entries));
    }

    private static OrderUnitsRequest.Entry entry(long item, String frame, String engine) {
        return new OrderUnitsRequest.Entry(item, frame, engine);
    }

    private void assertBad(OrderUnitsRequest request) {
        assertThatThrownBy(() -> service.replaceUnits(10L, request)).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- OrderUnits ----------------------------------------------------------------------------------

    @Test
    void vehiclesNeedNumbersButBatteriesAndAccessoriesDoNot() {
        for (VehicleType type : VehicleType.values()) {
            Product product = OrderFixtures.bike(9L, "p", "1", 1, type);
            OrderItem item = OrderFixtures.line(OrderFixtures.order(1L, "x", OrderStatus.PENDING, null), 1L, product, null, 1, "1");
            boolean expected = type == VehicleType.E_BIKE || type == VehicleType.E_SCOOTER || type == VehicleType.E_MOTORBIKE;
            assertThat(OrderUnits.requiresNumbers(item)).as(type.name()).isEqualTo(expected);
        }
    }

    @Test
    void aLineWithoutAProductSpecificationOrTypeNeedsNoNumbers() {
        Product product = OrderFixtures.bike(9L, "p", "1", 1, null);
        OrderItem item = OrderFixtures.line(OrderFixtures.order(1L, "x", OrderStatus.PENDING, null), 1L, product, null, 1, "1");
        assertThat(OrderUnits.requiresNumbers(item)).isFalse();
        product.setSpecification(null);
        assertThat(OrderUnits.requiresNumbers(item)).isFalse();
        assertThat(OrderUnits.warrantyMonthsOf(item)).isNull();
    }

    @Test
    void warrantyMonthsComeFromTheSpecification() {
        bikeLine.getProduct().getSpecification().setWarrantyMonths(24);
        assertThat(OrderUnits.warrantyMonthsOf(bikeLine)).isEqualTo(24);
    }

    @Test
    void completenessNeedsOneUnitPerBikeAndIgnoresAccessories() {
        assertThat(OrderUnits.isComplete(order)).isFalse();
        OrderFixtures.unit(bikeLine, 1L, "FRAME-0001", "ENGINE-0001");
        assertThat(OrderUnits.isComplete(order)).isFalse();
        OrderFixtures.unit(bikeLine, 2L, "FRAME-0002", "ENGINE-0002");
        assertThat(OrderUnits.isComplete(order)).isTrue();
    }

    @Test
    void anOrderOfOnlyAccessoriesIsAlwaysComplete() {
        order.getItems().remove(bikeLine);
        assertThat(OrderUnits.isComplete(order)).isTrue();
    }

    @Test
    void aBikeLineWithANullQuantityNeedsNothing() {
        bikeLine.setQuantity(null);
        assertThat(OrderUnits.isComplete(order)).isTrue();
    }

    // --- replaceUnits: status ------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "CANCELLED"})
    void numbersCannotBeRecordedBeforeProcessingOrOnACancelledOrder(OrderStatus status) {
        order.setStatus(status);
        assertBad(request(entry(100, "FRAME-0001", "ENGINE-0001")));
        verify(orders, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PROCESSING", "READY_FOR_PICKUP", "SHIPPED", "DELIVERED"})
    void numbersCanBeRecordedFromProcessingOnwards(OrderStatus status) {
        order.setStatus(status);
        assertThat(service.replaceUnits(10L, request(entry(100, "FRAME-0001", "ENGINE-0001")))).hasSize(1);
    }

    @Test
    void unknownOrderIs404AndTheUnitsAreLockedBeforeTheOrderIsRead() {
        when(orders.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.replaceUnits(404L, request())).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(unitRepository).findLockedByOrderId(404L);
    }

    // --- replaceUnits: validation --------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"", "ABCD", "AB_CD123", "ABCDE#", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "ÁBCDE12345", "ABC.DEF12"})
    void badFrameNumbersAreRejected(String frame) {
        assertBad(request(entry(100, frame, "ENGINE-0001")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABCD", "AB_CD123", "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE"})
    void badEngineNumbersAreRejected(String engine) {
        assertBad(request(entry(100, "FRAME-0001", engine)));
    }

    @Test
    void nullNumbersAreRejected() {
        assertBad(request(entry(100, null, "ENGINE-0001")));
        assertBad(request(entry(100, "FRAME-0001", null)));
    }

    @Test
    void lengthBoundariesAreFiveAndThirty() {
        assertThat(service.replaceUnits(10L, request(entry(100, "A".repeat(5), "B".repeat(5))))).hasSize(1);
        order.getItems().forEach(i -> i.getUnits().clear());
        assertThat(service.replaceUnits(10L, request(entry(100, "A".repeat(30), "B".repeat(30))))).hasSize(1);
    }

    @Test
    void numbersAreUpperCasedAndSpacesRemoved() {
        List<OrderItemUnitResponse> saved = service.replaceUnits(10L, request(entry(100, " rlh pc-1234 ", "6d12 345678")));
        assertThat(saved.get(0).frameNumber()).isEqualTo("RLHPC-1234");
        assertThat(saved.get(0).engineNumber()).isEqualTo("6D12345678");
    }

    @Test
    void anAccessoryLineOrUnknownLineIsRefused() {
        assertBad(request(entry(200, "FRAME-0001", "ENGINE-0001")));
        assertBad(request(entry(999, "FRAME-0001", "ENGINE-0001")));
        assertBad(request(new OrderUnitsRequest.Entry(null, "FRAME-0001", "ENGINE-0001")));
    }

    @Test
    void theSameFrameNumberTwiceInOneRequestIsRefusedEvenWithDifferentCaseOrSpacing() {
        assertBad(request(entry(100, "FRAME-0001", "ENGINE-0001"), entry(100, "frame-0001", "ENGINE-0002")));
        assertBad(request(entry(100, "FRAME-0001", "ENGINE-0001"), entry(100, "FRAME -0001", "ENGINE-0002")));
    }

    @Test
    void moreNumbersThanBikesOnTheLineIsRefused() {
        assertBad(request(entry(100, "FRAME-0001", "ENGINE-0001"), entry(100, "FRAME-0002", "ENGINE-0002"), entry(100, "FRAME-0003", "ENGINE-0003")));
    }

    @Test
    void aFrameNumberAlreadyOnAnotherOrderIsRefused() {
        OrderItemUnit foreign = new OrderItemUnit();
        foreign.setFrameNumber("FRAME-0001");
        org.springframework.test.util.ReflectionTestUtils.setField(foreign, "id", 999L);
        when(unitRepository.findByFrameNumberIn(anyCollection())).thenReturn(List.of(foreign));
        assertBad(request(entry(100, "FRAME-0001", "ENGINE-0001")));
    }

    @Test
    void aFrameNumberAlreadyOnThisOrderIsNotAConflict() {
        OrderItemUnit mine = OrderFixtures.unit(bikeLine, 1L, "FRAME-0001", "ENGINE-0001");
        when(unitRepository.findByFrameNumberIn(anyCollection())).thenReturn(List.of(mine));
        List<OrderItemUnitResponse> result = service.replaceUnits(10L, request(entry(100, "FRAME-0001", "ENGINE-0009")));
        assertThat(result).hasSize(1);
        assertThat(mine.getEngineNumber()).isEqualTo("ENGINE-0009"); // updated in place, the row (and its warranty history) is kept
    }

    // --- replaceUnits: effect ------------------------------------------------------------------------

    @Test
    void aPartialSaveIsAllowedAndPinsTheWarrantyLength() {
        bikeLine.getProduct().getSpecification().setWarrantyMonths(24);
        List<OrderItemUnitResponse> result = service.replaceUnits(10L, request(entry(100, "FRAME-0001", "ENGINE-0001")));
        assertThat(result).hasSize(1);
        assertThat(bikeLine.getUnits().iterator().next().getWarrantyMonths()).isEqualTo(24);
        assertThat(OrderUnits.isComplete(order)).isFalse();
        verify(auditLog).record(any(), any());
    }

    @Test
    void aLaterChangeOfTheProductWarrantyDoesNotMoveABikeAlreadyRecorded() {
        bikeLine.getProduct().getSpecification().setWarrantyMonths(24);
        service.replaceUnits(10L, request(entry(100, "FRAME-0001", "ENGINE-0001")));
        bikeLine.getProduct().getSpecification().setWarrantyMonths(60);
        service.replaceUnits(10L, request(entry(100, "FRAME-0001", "ENGINE-0002")));
        assertThat(bikeLine.getUnits().iterator().next().getWarrantyMonths()).isEqualTo(24);
    }

    @Test
    void correctingATypoReusesTheRow() {
        OrderItemUnit row = OrderFixtures.unit(bikeLine, 1L, "FRAME-0001", "ENGINE-0001");
        service.replaceUnits(10L, request(entry(100, "FRAME-0002", "ENGINE-0001")));
        assertThat(bikeLine.getUnits()).containsExactly(row);
        assertThat(row.getFrameNumber()).isEqualTo("FRAME-0002");
    }

    @Test
    void anEmptyListRemovesTheNumbersWhenNothingHangsOffThem() {
        OrderFixtures.unit(bikeLine, 1L, "FRAME-0001", "ENGINE-0001");
        assertThat(service.replaceUnits(10L, request())).isEmpty();
        assertThat(bikeLine.getUnits()).isEmpty();
    }

    @Test
    void aVehicleWithAWarrantyRequestKeepsItsFrameNumber() {
        OrderFixtures.unit(bikeLine, 1L, "FRAME-0001", "ENGINE-0001");
        when(warrantyClaims.existsByUnitIdIn(anyList())).thenReturn(true);
        assertThatThrownBy(() -> service.replaceUnits(10L, request(entry(100, "FRAME-0002", "ENGINE-0001"))))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(bikeLine.getUnits().iterator().next().getFrameNumber()).isEqualTo("FRAME-0001");
    }

    @Test
    void aVehicleWithAServiceReminderKeepsItsFrameNumber() {
        OrderFixtures.unit(bikeLine, 1L, "FRAME-0001", "ENGINE-0001");
        when(reminders.existsByUnitIdIn(anyList())).thenReturn(true);
        assertThatThrownBy(() -> service.replaceUnits(10L, request()))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void nullRequestMeansNoEntries() {
        assertThat(service.replaceUnits(10L, null)).isEmpty();
        assertThat(service.replaceUnits(10L, new OrderUnitsRequest(null))).isEmpty();
    }

    @Test
    void savingAllNumbersMakesTheOrderComplete() {
        service.replaceUnits(10L, request(entry(100, "FRAME-0001", "ENGINE-0001"), entry(100, "FRAME-0002", "ENGINE-0002")));
        assertThat(OrderUnits.isComplete(order)).isTrue();
    }
}
