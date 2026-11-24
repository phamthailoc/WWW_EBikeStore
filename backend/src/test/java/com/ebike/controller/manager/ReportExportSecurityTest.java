package com.ebike.controller.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.common.exception.ApiExceptionHandler;
import com.ebike.config.SecurityConfiguration;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.service.report.ReportExportService;
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
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * V28: the real security rules in front of the two Excel downloads and the headers of a download. The JWT filter is replaced by one that signs in whoever the
 * X-Test-Authorities header names; the service is a mock (what goes into the workbook is checked in ReportWorkbookTest).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ReportExportSecurityTest.TestConfiguration.class)
class ReportExportSecurityTest {

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
        ReportExportService exports() {
            return mock(ReportExportService.class);
        }

        @Bean
        ReportExportController reportExportController(ReportExportService exports) {
            return new ReportExportController(exports);
        }

        @Bean
        ApiExceptionHandler apiExceptionHandler() {
            return new ApiExceptionHandler();
        }
    }

    private static final String ROLE = "ROLE_MANAGER";
    private static final String VIEW_ALL = PermissionConstants.OrderManagement.ORDER_VIEW_ALL;
    private static final String REPORT = PermissionConstants.Reporting.REPORT_VIEW;

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired UserPreferenceRepository preferences;
    @Autowired ReportExportService exports;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(users, preferences, exports);
        byte[] bytes = "PK test".getBytes();
        when(exports.revenueReport(any(), any(), any(), any())).thenReturn(new ReportExportService.ExportFile("revenue-report_2026-11-01_2026-11-24.xlsx", bytes));
        when(exports.orders(any(), any(), any(), any(), any(), any())).thenReturn(new ReportExportService.ExportFile("orders_20261124-0930.xlsx", bytes));
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

    @Test void aVisitorMustSignInForEitherFile() throws Exception {
        assertEquals(401, statusOf(get("/api/v1/manager/revenue-report/export")));
        assertEquals(401, statusOf(get("/api/v1/manager/orders/export")));
    }

    @Test void aCustomerIsRefusedBoth() throws Exception {
        String own = PermissionConstants.Customer.ORDER_VIEW_OWN;
        assertEquals(403, statusOf(as(get("/api/v1/manager/revenue-report/export"), "ROLE_CUSTOMER", own)));
        assertEquals(403, statusOf(as(get("/api/v1/manager/orders/export"), "ROLE_CUSTOMER", own)));
    }

    @Test void theOrderListNeedsTheViewAllPermission() throws Exception {
        assertEquals(403, statusOf(as(get("/api/v1/manager/orders/export"), ROLE, REPORT)));
        assertEquals(200, statusOf(as(get("/api/v1/manager/orders/export"), ROLE, VIEW_ALL)));
    }

    @Test void theRevenueReportNeedsTheReportPermissionToo() throws Exception {
        assertEquals(403, statusOf(as(get("/api/v1/manager/revenue-report/export"), ROLE, VIEW_ALL)));
        assertEquals(200, statusOf(as(get("/api/v1/manager/revenue-report/export"), ROLE, VIEW_ALL, REPORT)));
    }

    @Test void onlyGetIsAllowed() throws Exception {
        assertEquals(405, statusOf(as(post("/api/v1/manager/orders/export"), ROLE, VIEW_ALL, REPORT)));
    }

    @Test void aDownloadIsAnXlsxAttachmentThatIsNeverCached() throws Exception {
        MvcResult result = mvc.perform(as(get("/api/v1/manager/orders/export"), ROLE, VIEW_ALL)).andReturn();

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", result.getResponse().getContentType());
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("attachment"));
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("orders_20261124-0930.xlsx"));
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"));
        assertEquals("nosniff", result.getResponse().getHeader("X-Content-Type-Options"));
    }

    @Test void theFiltersAndTheLanguageReachTheService() throws Exception {
        statusOf(as(get("/api/v1/manager/orders/export").param("status", "DELIVERED").param("paymentStatus", "PAID")
            .param("search", "an").param("showroomId", "3").param("lang", "en"), ROLE, VIEW_ALL));
        verify(exports).orders(any(), eq("DELIVERED"), eq("PAID"), eq("an"), eq(3L), eq("en"));

        statusOf(as(get("/api/v1/manager/revenue-report/export").param("period", "month").param("from", "2026-01-01").param("to", "2026-11-24"), ROLE, VIEW_ALL, REPORT));
        verify(exports).revenueReport(eq("month"), eq("2026-01-01"), eq("2026-11-24"), any());
    }

    @Test void aServiceRefusalReachesTheClientWithItsOwnStatus() throws Exception {
        when(exports.revenueReport(any(), any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "from date must be on or before to date"));

        assertEquals(400, statusOf(as(get("/api/v1/manager/revenue-report/export").param("from", "2026-12-01"), ROLE, VIEW_ALL, REPORT)));
    }
}
