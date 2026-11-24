package com.ebike.controller.order;

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
import com.ebike.service.document.DeliveryResendService;
import com.ebike.service.document.OrderDocumentService;
import com.ebike.service.document.PdfFile;
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
 * V27: the real security rules in front of the receipt and warranty-card downloads, and the headers of a download. The JWT filter is replaced by one
 * that signs in whoever the X-Test-Authorities header names; the service is a mock (who owns which order is checked in OrderDocumentServiceTest).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = OrderDocumentSecurityTest.TestConfiguration.class)
class OrderDocumentSecurityTest {

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
        OrderDocumentService documents() {
            return mock(OrderDocumentService.class);
        }

        @Bean
        OrderDocumentController orderDocumentController(OrderDocumentService documents) {
            return new OrderDocumentController(documents);
        }

        @Bean
        DeliveryResendService resender() {
            return mock(DeliveryResendService.class);
        }

        @Bean
        OrderDocumentEmailController orderDocumentEmailController(DeliveryResendService resender) {
            return new OrderDocumentEmailController(resender);
        }

        @Bean
        ApiExceptionHandler apiExceptionHandler() {
            return new ApiExceptionHandler();
        }
    }

    private static final String CUSTOMER_ROLE = "ROLE_CUSTOMER";
    private static final String STAFF_ROLE = "ROLE_MANAGER";
    private static final String OWN = PermissionConstants.Customer.ORDER_VIEW_OWN;
    private static final String VIEW_ALL = PermissionConstants.OrderManagement.ORDER_VIEW_ALL;

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired UserPreferenceRepository preferences;
    @Autowired OrderDocumentService documents;
    @Autowired DeliveryResendService resender;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(users, preferences, documents, resender);
        when(documents.receipt(any(), any(), any(), any())).thenReturn(new PdfFile("receipt-ORD-1.pdf", "%PDF-1.7 test".getBytes()));
        when(documents.warrantyCard(any(), any(), any(), any(), any())).thenReturn(new PdfFile("warranty-card-ORD-1.pdf", "%PDF-1.7 test".getBytes()));
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

    @Test void aVisitorMustSignInForEitherDocument() throws Exception {
        assertEquals(401, statusOf(get("/api/v1/orders/5/receipt")));
        assertEquals(401, statusOf(get("/api/v1/orders/5/warranty-card")));
    }

    @Test void aCustomerAndTheStaffMayAskForThem() throws Exception {
        assertEquals(200, statusOf(as(get("/api/v1/orders/5/receipt"), CUSTOMER_ROLE, OWN)));
        assertEquals(200, statusOf(as(get("/api/v1/orders/5/warranty-card"), CUSTOMER_ROLE, OWN)));
        assertEquals(200, statusOf(as(get("/api/v1/orders/5/receipt"), STAFF_ROLE, VIEW_ALL)));
        assertEquals(200, statusOf(as(get("/api/v1/orders/5/warranty-card"), STAFF_ROLE, VIEW_ALL)));
    }

    @Test void anAccountWithNeitherOrderPermissionIsRefused() throws Exception {
        assertEquals(403, statusOf(as(get("/api/v1/orders/5/receipt"), "ROLE_SUPPORT", PermissionConstants.Customer.PROFILE_VIEW)));
        assertEquals(403, statusOf(as(get("/api/v1/orders/5/warranty-card"), "ROLE_SUPPORT")));
    }

    @Test void onlyGetIsAllowed() throws Exception {
        assertEquals(403, statusOf(as(post("/api/v1/orders/5/receipt"), CUSTOMER_ROLE, OWN)));
    }

    @Test void aDownloadIsAPrivatePdfAttachmentThatIsNeverCached() throws Exception {
        MvcResult result = mvc.perform(as(get("/api/v1/orders/5/receipt"), CUSTOMER_ROLE, OWN)).andReturn();

        assertEquals("application/pdf", result.getResponse().getContentType());
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("attachment"));
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("receipt-ORD-1.pdf"));
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"));
        assertEquals("nosniff", result.getResponse().getHeader("X-Content-Type-Options"));
    }

    @Test void aServiceRefusalReachesTheClientWithItsOwnStatus() throws Exception {
        when(documents.receipt(any(), any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        assertEquals(404, statusOf(as(get("/api/v1/orders/5/receipt"), CUSTOMER_ROLE, OWN)));
    }

    private static final String UPDATE = PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS;

    @Test void onlyStaffWhoMayUpdateAnOrderCanSendTheEmailAgain() throws Exception {
        when(resender.resend(eq(5L), any())).thenReturn("an@example.com");

        assertEquals(401, statusOf(post("/api/v1/orders/5/documents/email")));
        assertEquals(403, statusOf(as(post("/api/v1/orders/5/documents/email"), CUSTOMER_ROLE, OWN)));
        assertEquals(403, statusOf(as(post("/api/v1/orders/5/documents/email"), STAFF_ROLE, VIEW_ALL)));
        assertEquals(200, statusOf(as(post("/api/v1/orders/5/documents/email"), STAFF_ROLE, VIEW_ALL, UPDATE)));
        verify(resender).resend(eq(5L), any());
    }

    @Test void aRefusalOfTheResendReachesTheStaffMemberWithItsStatus() throws Exception {
        when(resender.resend(eq(5L), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "mail server"));

        assertEquals(502, statusOf(as(post("/api/v1/orders/5/documents/email"), STAFF_ROLE, VIEW_ALL, UPDATE)));
    }
}
