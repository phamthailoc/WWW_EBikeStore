package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.controller.testride.TestRideController;
import com.ebike.dto.testride.TestRideBacklogResponse;
import com.ebike.dto.testride.TestRideCreateRequest;
import com.ebike.dto.testride.TestRideResponse;
import com.ebike.dto.testride.TestRideSlotResponse;
import com.ebike.dto.testride.TestRideUpdateRequest;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.service.testride.TestRideService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/** TestRideController (standalone MockMvc, mocked service) and the TestRideBooking entity. */
class TestRideControllerAndEntityTest {

    TestRideService service;
    MockMvc mvc;
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("an", "n/a", List.of());

    @BeforeEach
    void setUp() {
        service = mock(TestRideService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TestRideController(service)).build();
    }

    static TestRideResponse response(long id, String status) {
        return new TestRideResponse(id, 5L, "Aero X", "aero-x", 1L, "Kinetic Q1", "1 Le Loi", LocalDateTime.parse("2026-11-24T10:00"), status,
            "0901234567", null, null, "An Nguyen", "an@example.com", true, List.of(), LocalDateTime.parse("2026-11-23T10:00"), null, null);
    }

    // ------------------------------------------------------------------ routes, status codes and caching

    @Test
    void slotsIsPublicNoStoreAndPassesTheParameters() throws Exception {
        when(service.slots(1L, "2026-11-24")).thenReturn(List.of(new TestRideSlotResponse(LocalDateTime.parse("2026-11-24T08:00"), 2, 2, true)));
        mvc.perform(get("/api/v1/test-rides/slots").param("showroomId", "1").param("date", "2026-11-24"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].capacity").value(2))
            .andExpect(jsonPath("$[0].bookable").value(true));
    }

    @Test
    void slotsWithoutParametersStillReachesTheServiceWhichAnswers400() throws Exception {
        when(service.slots(null, null)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn cửa hàng."));
        mvc.perform(get("/api/v1/test-rides/slots")).andExpect(status().isBadRequest());
    }

    @Test
    void slotsWithANonNumericShowroomIs400() throws Exception {
        mvc.perform(get("/api/v1/test-rides/slots").param("showroomId", "abc").param("date", "2026-11-24")).andExpect(status().isBadRequest());
    }

    @Test
    void mineUsesTheSignedInNameAndIsNoStore() throws Exception {
        when(service.mine("an")).thenReturn(List.of(response(1, "REQUESTED")));
        mvc.perform(get("/api/v1/test-rides").principal(auth))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].status").value("REQUESTED"));
    }

    @Test
    void bookAnswers201AndHandsTheBodyAndTheUsernameToTheService() throws Exception {
        when(service.book(eq("an"), any(TestRideCreateRequest.class))).thenReturn(response(9, "REQUESTED"));
        mvc.perform(post("/api/v1/test-rides").principal(auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":5,\"showroomId\":1,\"slotStart\":\"2026-11-24T10:00\",\"contactPhone\":\"0901234567\",\"note\":\"hi\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(9));
        ArgumentCaptor<TestRideCreateRequest> body = ArgumentCaptor.forClass(TestRideCreateRequest.class);
        verify(service).book(eq("an"), body.capture());
        assertThat(body.getValue()).isEqualTo(new TestRideCreateRequest(5L, 1L, "2026-11-24T10:00", "0901234567", "hi"));
    }

    @Test
    void bookWithMalformedJsonIs400AndNeverReachesTheService() throws Exception {
        mvc.perform(post("/api/v1/test-rides").principal(auth).contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void bookWithoutABodyIs400() throws Exception {
        mvc.perform(post("/api/v1/test-rides").principal(auth).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    void bookWithAWrongContentTypeIs415() throws Exception {
        mvc.perform(post("/api/v1/test-rides").principal(auth).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void serviceErrorsKeepTheirStatusCode() throws Exception {
        when(service.book(eq("an"), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "full"));
        mvc.perform(post("/api/v1/test-rides").principal(auth).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        when(service.book(eq("an"), any())).thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "slow down"));
        mvc.perform(post("/api/v1/test-rides").principal(auth).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isTooManyRequests());
    }

    @Test
    void cancelIsAPatchOnTheBookingId() throws Exception {
        when(service.cancelOwn("an", 12L)).thenReturn(response(12, "CANCELLED"));
        mvc.perform(patch("/api/v1/test-rides/12/cancel").principal(auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/test-rides/12/cancel").principal(auth)).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/v1/test-rides/abc/cancel").principal(auth)).andExpect(status().isBadRequest());
    }

    @Test
    void managerScheduleForwardsEveryFilterAndIsNoStore() throws Exception {
        when(service.schedule("2026-11-24", 3L, "DONE", 50)).thenReturn(List.of(response(1, "DONE")));
        mvc.perform(get("/api/v1/manager/test-rides").param("date", "2026-11-24").param("showroomId", "3").param("status", "DONE").param("limit", "50"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].status").value("DONE"));
        verify(service).schedule("2026-11-24", 3L, "DONE", 50);
    }

    @Test
    void managerScheduleWithNoFiltersPassesNulls() throws Exception {
        when(service.schedule(isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());
        mvc.perform(get("/api/v1/manager/test-rides")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void managerBacklogIsNoStore() throws Exception {
        when(service.backlog()).thenReturn(new TestRideBacklogResponse(1, 2, 3, 4));
        mvc.perform(get("/api/v1/manager/test-rides/backlog"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.pending").value(1))
            .andExpect(jsonPath("$.overdue").value(2))
            .andExpect(jsonPath("$.customerCancelled").value(3))
            .andExpect(jsonPath("$.unavailable").value(4));
    }

    @Test
    void managerUpdateIsAPatchWithTheStatusAndNote() throws Exception {
        when(service.update(eq(40L), any(TestRideUpdateRequest.class))).thenReturn(response(40, "CONFIRMED"));
        mvc.perform(patch("/api/v1/manager/test-rides/40").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\",\"staffNote\":\"ok\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
        verify(service).update(40L, new TestRideUpdateRequest("CONFIRMED", "ok"));
    }

    @Test
    void managerUpdateOfAMissingBookingIs404() throws Exception {
        when(service.update(eq(404L), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "x"));
        mvc.perform(patch("/api/v1/manager/test-rides/404").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ entity

    @Test
    void entityDefaultsToRequestedAndMapsToTheRightTable() {
        TestRideBooking b = new TestRideBooking();
        assertThat(b.getStatus()).isEqualTo("REQUESTED");
        assertThat(b.getId()).isNull();
        assertThat(TestRideBooking.class.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(TestRideBooking.class.getAnnotation(Table.class).name()).isEqualTo("test_ride_bookings");
    }

    @Test
    void entityConstantsAreTheStringsTheDatabaseAndQueriesUse() {
        assertThat(List.of(TestRideBooking.REQUESTED, TestRideBooking.CONFIRMED, TestRideBooking.DONE, TestRideBooking.CANCELLED, TestRideBooking.NO_SHOW))
            .containsExactly("REQUESTED", "CONFIRMED", "DONE", "CANCELLED", "NO_SHOW");
        assertThat(List.of(TestRideBooking.BY_CUSTOMER, TestRideBooking.BY_STORE, TestRideBooking.BY_EXPIRY, TestRideBooking.BY_ERASURE))
            .containsExactly("CUSTOMER", "STORE", "EXPIRED", "ERASED");
    }

    @Test
    void columnLengthsMatchTheLimitsTheServiceEnforces() throws Exception {
        assertThat(column("customerNote").length()).isEqualTo(500); // TestRideService.MAX_NOTE
        assertThat(column("staffNote").length()).isEqualTo(1000); // TestRideService.MAX_STAFF_NOTE
        assertThat(column("contactPhone").length()).isGreaterThanOrEqualTo(11); // a 10-11 digit local number fits
        assertThat(column("status").length()).isGreaterThanOrEqualTo("CANCELLED".length());
        for (String source : List.of(TestRideBooking.BY_CUSTOMER, TestRideBooking.BY_STORE, TestRideBooking.BY_EXPIRY, TestRideBooking.BY_ERASURE)) {
            assertThat(column("cancelSource").length()).isGreaterThanOrEqualTo(source.length());
        }
    }

    @Test
    void mandatoryColumnsAreNotNullAndCreatedAtIsNotUpdatable() throws Exception {
        for (String field : List.of("slotStart", "status", "contactPhone", "createdAt", "updatedAt")) {
            assertThat(column(field).nullable()).as(field).isFalse();
        }
        assertThat(column("createdAt").updatable()).isFalse();
        assertThat(column("confirmedAt").nullable()).isTrue();
        assertThat(column("reminderSentAt").nullable()).isTrue();
    }

    @Test
    void gettersAndSettersRoundTrip() {
        TestRideBooking b = new TestRideBooking();
        LocalDateTime t = LocalDateTime.parse("2026-11-23T10:00");
        b.setSlotStart(t);
        b.setStatus("DONE");
        b.setContactPhone("0901234567");
        b.setCustomerNote("c");
        b.setStaffNote("s");
        b.setConfirmedAt(t);
        b.setCancelledByCustomerAt(t);
        b.setCancelSource("STORE");
        b.setReminderSentAt(t);
        b.setCreatedAt(t);
        b.setUpdatedAt(t);
        assertThat(b.getSlotStart()).isEqualTo(t);
        assertThat(b.getStatus()).isEqualTo("DONE");
        assertThat(b.getContactPhone()).isEqualTo("0901234567");
        assertThat(b.getCustomerNote()).isEqualTo("c");
        assertThat(b.getStaffNote()).isEqualTo("s");
        assertThat(b.getConfirmedAt()).isEqualTo(t);
        assertThat(b.getCancelledByCustomerAt()).isEqualTo(t);
        assertThat(b.getCancelSource()).isEqualTo("STORE");
        assertThat(b.getReminderSentAt()).isEqualTo(t);
        assertThat(b.getCreatedAt()).isEqualTo(t);
        assertThat(b.getUpdatedAt()).isEqualTo(t);
    }

    private static Column column(String field) throws NoSuchFieldException {
        Field f = TestRideBooking.class.getDeclaredField(field);
        return f.getAnnotation(Column.class);
    }
}
