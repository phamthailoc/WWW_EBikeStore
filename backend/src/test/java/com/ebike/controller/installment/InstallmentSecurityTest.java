package com.ebike.controller.installment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.common.exception.ApiExceptionHandler;
import com.ebike.config.SecurityConfiguration;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.service.installment.InstallmentPlanner;
import com.ebike.service.installment.InstallmentService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * V24: the real security rules in front of the instalment API. The JWT filter is replaced by one that signs in whoever the X-Test-Authorities header names
 * (comma separated), exactly where the real filter puts the user. The services are mocks: only who may call what is checked here.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = InstallmentSecurityTest.TestConfiguration.class)
class InstallmentSecurityTest {

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
        InstallmentPlanner installmentPlanner() {
            return mock(InstallmentPlanner.class);
        }

        @Bean
        InstallmentService installmentService() {
            // every answer is the mock's default: an empty list, or null (the order has no application, so 204)
            return mock(InstallmentService.class);
        }

        @Bean
        InstallmentController installmentController(InstallmentPlanner planner, InstallmentService service) {
            return new InstallmentController(planner, service);
        }

        @Bean
        ApiExceptionHandler apiExceptionHandler() {
            return new ApiExceptionHandler();
        }
    }

    private static final String CUSTOMER_ROLE = "ROLE_CUSTOMER";
    private static final String STAFF_ROLE = "ROLE_MANAGER";
    private static final String OWN = PermissionConstants.Customer.ORDER_VIEW_OWN;
    private static final String VIEW_QUEUE = PermissionConstants.OrderManagement.ORDER_VIEW_ALL;
    private static final String MOVE = PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS;
    private static final String DECISION = "{\"decision\":\"APPROVE\"}";

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

    private static MockHttpServletRequestBuilder quote() {
        return get("/api/v1/installments/quote").param("amount", "30000000").param("downPaymentPercent", "30").param("termMonths", "12");
    }

    @Test void theOfferAndTheQuoteAreOpenToEveryoneAndNeverCached() throws Exception {
        assertEquals(200, statusOf(get("/api/v1/installments/config")));
        assertEquals(200, statusOf(quote()));
        MvcResult result = mvc.perform(get("/api/v1/installments/config")).andReturn();
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"));
    }

    @Test void aVisitorMustSignInToReadOrDecideAnApplication() throws Exception {
        assertEquals(401, statusOf(get("/api/v1/installments/orders/5")));
        assertEquals(401, statusOf(get("/api/v1/installments/mine")));
        assertEquals(401, statusOf(get("/api/v1/manager/installments")));
        assertEquals(401, statusOf(get("/api/v1/manager/installments/backlog")));
        assertEquals(401, statusOf(patch("/api/v1/manager/installments/5").contentType(MediaType.APPLICATION_JSON).content(DECISION)));
    }

    @Test void aCustomerReadsTheirOrdersButNeverTheQueue() throws Exception {
        // an order that is not theirs (or has no application) answers 204, never a hint that the order exists
        assertEquals(204, statusOf(as(get("/api/v1/installments/orders/5"), CUSTOMER_ROLE, OWN)));
        assertEquals(200, statusOf(as(get("/api/v1/installments/mine"), CUSTOMER_ROLE, OWN)));
        assertEquals(403, statusOf(as(get("/api/v1/manager/installments"), CUSTOMER_ROLE, OWN)));
        assertEquals(403, statusOf(as(get("/api/v1/manager/installments/backlog"), CUSTOMER_ROLE, OWN)));
        assertEquals(403, statusOf(as(patch("/api/v1/manager/installments/5"), CUSTOMER_ROLE, OWN).contentType(MediaType.APPLICATION_JSON).content(DECISION)));
    }

    @Test void staffWhoOnlyViewOrdersReadTheQueueButCannotDecide() throws Exception {
        assertEquals(200, statusOf(as(get("/api/v1/manager/installments"), STAFF_ROLE, VIEW_QUEUE)));
        assertEquals(200, statusOf(as(get("/api/v1/manager/installments/backlog"), STAFF_ROLE, VIEW_QUEUE)));
        assertEquals(204, statusOf(as(get("/api/v1/installments/orders/5"), STAFF_ROLE, VIEW_QUEUE)));
        assertEquals(403, statusOf(as(patch("/api/v1/manager/installments/5"), STAFF_ROLE, VIEW_QUEUE).contentType(MediaType.APPLICATION_JSON).content(DECISION)));
    }

    @Test void theWritePermissionLetsStaffDecide() throws Exception {
        int decided = statusOf(as(patch("/api/v1/manager/installments/5"), STAFF_ROLE, VIEW_QUEUE, MOVE).contentType(MediaType.APPLICATION_JSON).content(DECISION));
        assertTrue(decided != 401 && decided != 403, "the write permission is enough, got " + decided);
    }
}
