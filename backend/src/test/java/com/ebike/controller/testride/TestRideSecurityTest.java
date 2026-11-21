package com.ebike.controller.testride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.common.exception.ApiExceptionHandler;
import com.ebike.config.SecurityConfiguration;
import com.ebike.config.UserLocaleResolver;
import com.ebike.dto.testride.TestRideResponse;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.service.testride.TestRideService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * V23: the real security rules in front of the test-ride API. The JWT filter is replaced by one that signs in whoever the
 * X-Test-Authorities header names (comma separated), exactly where the real filter puts the user.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = TestRideSecurityTest.TestConfiguration.class)
class TestRideSecurityTest {

    static class HeaderAuthoritiesFilter extends JwtAuthenticationFilter {
        HeaderAuthoritiesFilter() {
            super(null, null, null);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
            String header = request.getHeader("X-Test-Authorities");
            if (header != null) {
                List<GrantedAuthority> authorities = new ArrayList<>(Arrays.stream(header.split(","))
                    .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("an", "x", authorities));
            }
            chain.doFilter(request, response);
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfiguration.class)
    static class TestConfiguration {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new HeaderAuthoritiesFilter();
        }

        @Bean
        UserRepository users() {
            return mock(UserRepository.class);
        }

        @Bean
        UserPreferenceRepository preferences() {
            return mock(UserPreferenceRepository.class);
        }

        @Bean
        UserLocaleResolver localeResolver(UserRepository users, UserPreferenceRepository preferences) {
            return new UserLocaleResolver(users, preferences);
        }

        @Bean
        TestRideService testRideService() {
            TestRideService service = mock(TestRideService.class);
            when(service.slots(any(), any())).thenReturn(List.of());
            when(service.mine(any())).thenReturn(List.of());
            when(service.schedule(any(), any(), any())).thenReturn(List.of());
            when(service.backlog()).thenReturn(new com.ebike.dto.testride.TestRideBacklogResponse(0, 0, 0, 0));
            when(service.book(any(), any())).thenReturn(new TestRideResponse(
                1L, 5L, "Bike", "bike", 10L, "Quan 1", "addr", null, "REQUESTED", "0901234567", null, null, "An", "an@example.com", true, List.of(), null, null, null));
            return service;
        }

        @Bean
        TestRideController testRideController(TestRideService service) {
            return new TestRideController(service);
        }

        @Bean
        ApiExceptionHandler apiExceptionHandler() {
            return new ApiExceptionHandler();
        }
    }

    // Manager and Admin hold the customer permissions too (the stock-alert rule explains why), so the role is what decides.
    private static final String CUSTOMER_ROLE = "ROLE_CUSTOMER";
    private static final String STAFF_ROLE = "ROLE_MANAGER";
    private static final String PROFILE = PermissionConstants.Customer.PROFILE_VIEW;
    private static final String VIEW_QUEUE = PermissionConstants.OrderManagement.ORDER_VIEW_ALL;
    private static final String MOVE = PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS;
    private static final String BOOK = "{\"productId\":5,\"showroomId\":10,\"slotStart\":\"2026-11-22T09:00\",\"contactPhone\":\"0901234567\"}";
    private static final String STEP = "{\"status\":\"CONFIRMED\"}";

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired UserPreferenceRepository preferences;
    MockMvc mvc;

    @BeforeEach void setup() {
        org.mockito.Mockito.reset(users, preferences);
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
    }

    private int statusOf(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String... authorities) {
        return request.header("X-Test-Authorities", String.join(",", authorities));
    }

    @Test void aVisitorMustSignInForEveryTestRideCall() throws Exception {
        assertEquals(401, statusOf(get("/api/v1/test-rides")));
        assertEquals(401, statusOf(get("/api/v1/test-rides/slots").param("showroomId", "10").param("date", "2026-11-22")));
        assertEquals(401, statusOf(post("/api/v1/test-rides").contentType(MediaType.APPLICATION_JSON).content(BOOK)));
        assertEquals(401, statusOf(patch("/api/v1/test-rides/3/cancel")));
        assertEquals(401, statusOf(get("/api/v1/manager/test-rides")));
        assertEquals(401, statusOf(patch("/api/v1/manager/test-rides/3").contentType(MediaType.APPLICATION_JSON).content(STEP)));
    }

    @Test void aCustomerBooksListsAndCancelsButDoesNotSeeTheSchedule() throws Exception {
        assertEquals(200, statusOf(as(get("/api/v1/test-rides"), CUSTOMER_ROLE, PROFILE)));
        assertEquals(200, statusOf(as(get("/api/v1/test-rides/slots").param("showroomId", "10").param("date", "2026-11-22"), CUSTOMER_ROLE, PROFILE)));
        assertEquals(201, statusOf(as(post("/api/v1/test-rides"), CUSTOMER_ROLE, PROFILE).contentType(MediaType.APPLICATION_JSON).content(BOOK)));
        int cancelled = statusOf(as(patch("/api/v1/test-rides/3/cancel"), CUSTOMER_ROLE, PROFILE));
        assertTrue(cancelled != 401 && cancelled != 403, "a customer may cancel their own booking, got " + cancelled);
        assertEquals(403, statusOf(as(get("/api/v1/manager/test-rides"), CUSTOMER_ROLE, PROFILE)));
        assertEquals(403, statusOf(as(patch("/api/v1/manager/test-rides/3"), CUSTOMER_ROLE, PROFILE).contentType(MediaType.APPLICATION_JSON).content(STEP)));
    }

    @Test void managerAndAdminCannotBookEvenWithTheCustomerPermissions() throws Exception {
        for (String role : List.of("ROLE_MANAGER", "ROLE_ADMIN")) {
            assertEquals(403, statusOf(as(get("/api/v1/test-rides"), role, PROFILE, VIEW_QUEUE, MOVE)), role);
            assertEquals(403, statusOf(as(get("/api/v1/test-rides/slots").param("showroomId", "10").param("date", "2026-11-22"), role, PROFILE, VIEW_QUEUE)), role);
            assertEquals(403, statusOf(as(post("/api/v1/test-rides"), role, PROFILE, VIEW_QUEUE, MOVE).contentType(MediaType.APPLICATION_JSON).content(BOOK)), role);
            assertEquals(403, statusOf(as(patch("/api/v1/test-rides/3/cancel"), role, PROFILE, VIEW_QUEUE, MOVE)), role);
        }
    }

    @Test void staffWhoOnlyViewOrdersMayReadTheScheduleButNotMoveABooking() throws Exception {
        assertEquals(200, statusOf(as(get("/api/v1/manager/test-rides"), STAFF_ROLE, VIEW_QUEUE)));
        assertEquals(401, statusOf(get("/api/v1/manager/test-rides/backlog")));
        assertEquals(403, statusOf(as(get("/api/v1/manager/test-rides/backlog"), CUSTOMER_ROLE, PROFILE)));
        assertEquals(200, statusOf(as(get("/api/v1/manager/test-rides/backlog"), STAFF_ROLE, VIEW_QUEUE)));
        assertEquals(403, statusOf(as(patch("/api/v1/manager/test-rides/3"), STAFF_ROLE, VIEW_QUEUE).contentType(MediaType.APPLICATION_JSON).content(STEP)));
        int moved = statusOf(as(patch("/api/v1/manager/test-rides/3"), STAFF_ROLE, VIEW_QUEUE, MOVE).contentType(MediaType.APPLICATION_JSON).content(STEP));
        assertTrue(moved != 401 && moved != 403, "the write permission is enough, got " + moved);
    }

    @Test void theSlotAnswerIsNeverCached() throws Exception {
        MvcResult result = mvc.perform(as(get("/api/v1/test-rides/slots").param("showroomId", "10").param("date", "2026-11-22"), CUSTOMER_ROLE, PROFILE)).andReturn();
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"));
    }

    @Test void theBodiesOfTheRefusalsFollowTheDeviceLanguage() throws Exception {
        String vi = mvc.perform(get("/api/v1/test-rides")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(vi.contains("Bạn cần đăng nhập."), vi);
        String en = mvc.perform(get("/api/v1/test-rides").cookie(new Cookie("ebike_lang", "en"))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(en.contains("Please sign in."), en);
        String forbidden = mvc.perform(as(get("/api/v1/test-rides"), STAFF_ROLE, PROFILE).cookie(new Cookie("ebike_lang", "en"))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(forbidden.contains("You do not have permission"), forbidden);
        assertFalse(forbidden.contains("Bạn không có quyền"));
    }

    @Test void anUnreadableBodyOrABadIdAnswers400InTheRequestLanguage() throws Exception {
        MvcResult broken = mvc.perform(as(post("/api/v1/test-rides"), CUSTOMER_ROLE, PROFILE).contentType(MediaType.APPLICATION_JSON).content("{not json")).andReturn();
        assertEquals(400, broken.getResponse().getStatus());
        assertTrue(broken.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("không hợp lệ"));
        MvcResult badId = mvc.perform(as(patch("/api/v1/test-rides/abc/cancel"), CUSTOMER_ROLE, PROFILE).cookie(new Cookie("ebike_lang", "en"))).andReturn();
        assertEquals(400, badId.getResponse().getStatus());
        assertTrue(badId.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("is not valid"));
    }
}
