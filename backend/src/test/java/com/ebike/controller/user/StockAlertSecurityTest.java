package com.ebike.controller.user;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.config.SecurityConfiguration;
import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.service.stock.StockAlertService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * V22: the real security rules in front of /api/v1/stock-alerts. Only the CUSTOMER role may use them: Manager and
 * Admin hold the favourites permission, so a permission check alone would let them in. The JWT filter is replaced by
 * one that reads the roles from the X-Test-Roles header, exactly where the real filter puts the signed-in user.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = StockAlertSecurityTest.TestConfiguration.class)
class StockAlertSecurityTest {

    static class HeaderRolesFilter extends JwtAuthenticationFilter {
        HeaderRolesFilter() {
            super(null, null, null);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
            String roles = request.getHeader("X-Test-Roles");
            if (roles != null) {
                List<GrantedAuthority> authorities = Arrays.stream(roles.split(","))
                    .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
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
            return new HeaderRolesFilter();
        }

        @Bean
        StockAlertService stockAlertService() {
            return mock(StockAlertService.class);
        }

        @Bean
        StockAlertController stockAlertController(StockAlertService service) {
            return new StockAlertController(service);
        }
    }

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
    }

    private static final String BODY = "{\"variantId\":11}";

    @Test void aVisitorMustSignIn() throws Exception {
        mvc.perform(get("/api/v1/stock-alerts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/stock-alerts").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/stock-alerts/11")).andExpect(status().isUnauthorized());
    }

    @Test void aCustomerCanListSubscribeAndCancel() throws Exception {
        mvc.perform(get("/api/v1/stock-alerts").header("X-Test-Roles", "CUSTOMER")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/stock-alerts").header("X-Test-Roles", "CUSTOMER").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/stock-alerts/11").header("X-Test-Roles", "CUSTOMER")).andExpect(status().isNoContent());
    }

    @Test void managerAndAdminAreRefused() throws Exception {
        for (String role : List.of("MANAGER", "ADMIN")) {
            mvc.perform(get("/api/v1/stock-alerts").header("X-Test-Roles", role)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/stock-alerts").header("X-Test-Roles", role).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/stock-alerts/11").header("X-Test-Roles", role)).andExpect(status().isForbidden());
        }
    }
}
