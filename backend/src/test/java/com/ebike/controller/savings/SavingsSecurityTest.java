package com.ebike.controller.savings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.common.exception.ApiExceptionHandler;
import com.ebike.config.SecurityConfiguration;
import com.ebike.config.UserLocaleResolver;
import com.ebike.dto.savings.SavingsResponse;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.service.savings.SavingsService;
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
 * V33: the real security rules in front of the savings endpoint. A visitor may read it, so may any role that has product:view, and a role without that
 * permission gets a 403; no answer may be cached because the prices change. The JWT filter is replaced by one that signs in whoever the X-Test-Authorities
 * header names (comma separated); the service is a mock.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SavingsSecurityTest.TestConfiguration.class)
class SavingsSecurityTest {

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
                    .filter(name -> !name.isBlank())
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
        SavingsService savingsService() {
            SavingsService service = mock(SavingsService.class);
            SavingsResponse notCalculable = new SavingsResponse(true, false, "x", "E_SCOOTER", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, 80, 85, 300, 30);
            when(service.forProduct(eq("amy"), any(), any())).thenReturn(notCalculable);
            when(service.forProduct(eq("missing"), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
            return service;
        }

        @Bean
        SavingsController savingsController(SavingsService service) {
            return new SavingsController(service);
        }

        @Bean
        ApiExceptionHandler apiExceptionHandler() {
            return new ApiExceptionHandler();
        }
    }

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
    }

    private int statusOf(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test void aVisitorMaySeeTheSavingsAndItIsNeverCached() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/products/amy/savings")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"), result.getResponse().getHeader("Cache-Control"));
    }

    @Test void aRoleWithProductViewMaySeeThemAndOneWithoutGetsA403() throws Exception {
        assertEquals(200, statusOf(get("/api/v1/products/amy/savings?kmPerDay=30&daysPerMonth=20")
            .header("X-Test-Authorities", "ROLE_MANAGER," + PermissionConstants.Guest.PRODUCT_VIEW)));
        assertEquals(403, statusOf(get("/api/v1/products/amy/savings").header("X-Test-Authorities", "ROLE_SUPPORT,ticket:view")));
    }

    @Test void anUnknownBikeIsA404() throws Exception {
        assertEquals(404, statusOf(get("/api/v1/products/missing/savings")));
    }

    /** Spring Security adds no-store to its own responses, so the controller's header is checked without it. */
    @Test void theControllerItselfForbidsCaching() throws Exception {
        SavingsService service = context.getBean(SavingsService.class);
        MockMvc plain = MockMvcBuilders.standaloneSetup(new SavingsController(service)).build();
        String cacheControl = plain.perform(get("/api/v1/products/amy/savings")).andReturn().getResponse().getHeader("Cache-Control");
        assertTrue(cacheControl != null && cacheControl.contains("no-store"), String.valueOf(cacheControl));
    }
}
