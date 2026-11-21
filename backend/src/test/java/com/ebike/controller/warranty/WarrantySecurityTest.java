package com.ebike.controller.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.common.exception.ApiExceptionHandler;
import com.ebike.config.SecurityConfiguration;
import com.ebike.config.UserLocaleResolver;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.service.warranty.WarrantyService;
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
import java.util.Optional;
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
 * V20: the real security rules in front of the warranty API, and the language of the 401, 403 and 400 bodies. The JWT filter is
 * replaced by one that signs in whoever the X-Test-Authorities header names (comma separated), exactly where the real filter puts the user.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = WarrantySecurityTest.TestConfiguration.class)
class WarrantySecurityTest {

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
        WarrantyService warrantyService() {
            WarrantyService service = mock(WarrantyService.class);
            when(service.listClaims(any(), anyInt(), anyInt())).thenReturn(new WarrantyService.ClaimPage(List.of(), false));
            return service;
        }

        @Bean
        WarrantyController warrantyController(WarrantyService service) {
            return new WarrantyController(service);
        }

        @Bean
        ApiExceptionHandler apiExceptionHandler() {
            return new ApiExceptionHandler();
        }
    }

    private static final String CUSTOMER = PermissionConstants.Customer.PROFILE_VIEW;
    private static final String VIEW_QUEUE = PermissionConstants.OrderManagement.ORDER_VIEW_ALL;
    private static final String MOVE_CLAIMS = PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS;
    private static final String CLAIM = "{\"unitId\":1,\"description\":\"The battery does not charge fully\"}";
    private static final String MOVE = "{\"status\":\"RECEIVED\"}";

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

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String... authorities) {
        return request.header("X-Test-Authorities", String.join(",", authorities));
    }

    @Test void aVisitorMustSignInToUseTheBookButMayLookUpAFrame() throws Exception {
        assertEquals(401, statusOf(get("/api/v1/warranty/units")));
        assertEquals(401, statusOf(get("/api/v1/warranty/claims")));
        assertEquals(401, statusOf(post("/api/v1/warranty/claims").contentType(MediaType.APPLICATION_JSON).content(CLAIM)));
        int lookup = statusOf(get("/api/v1/warranty/lookup").param("frame", "ABCDE12345"));
        assertTrue(lookup != 401 && lookup != 403, "the lookup is public, got " + lookup);
    }

    @Test void aCustomerUsesTheBookButNotTheQueue() throws Exception {
        assertEquals(200, statusOf(as(get("/api/v1/warranty/units"), CUSTOMER)));
        assertEquals(200, statusOf(as(get("/api/v1/warranty/claims"), CUSTOMER)));
        assertEquals(201, statusOf(as(post("/api/v1/warranty/claims"), CUSTOMER).contentType(MediaType.APPLICATION_JSON).content(CLAIM)));
        assertEquals(403, statusOf(as(get("/api/v1/manager/warranty-claims"), CUSTOMER)));
        assertEquals(403, statusOf(as(patch("/api/v1/manager/warranty-claims/3"), CUSTOMER).contentType(MediaType.APPLICATION_JSON).content(MOVE)));
    }

    @Test void staffWhoOnlyViewOrdersMayReadTheQueueButNotMoveARequest() throws Exception {
        assertEquals(200, statusOf(as(get("/api/v1/manager/warranty-claims"), VIEW_QUEUE)));
        assertEquals(403, statusOf(as(patch("/api/v1/manager/warranty-claims/3"), VIEW_QUEUE).contentType(MediaType.APPLICATION_JSON).content(MOVE)));
        int moved = statusOf(as(patch("/api/v1/manager/warranty-claims/3"), VIEW_QUEUE, MOVE_CLAIMS).contentType(MediaType.APPLICATION_JSON).content(MOVE));
        assertTrue(moved != 401 && moved != 403, "the write permission is enough, got " + moved);
    }

    @Test void staffWithoutTheCustomerPermissionCannotUseTheBook() throws Exception {
        assertEquals(403, statusOf(as(get("/api/v1/warranty/units"), VIEW_QUEUE, MOVE_CLAIMS)));
    }

    // ---- the words of the 401, 403 and 400 bodies ----

    @Test void theSignInRequiredBodyFollowsTheDeviceChoiceAndIgnoresTheBrowser() throws Exception {
        assertTrue(body(get("/api/v1/warranty/units")).contains("Bạn cần đăng nhập."));
        assertTrue(body(get("/api/v1/warranty/units").cookie(new Cookie("ebike_lang", "en"))).contains("Please sign in."));
        assertTrue(body(get("/api/v1/warranty/units").header("Accept-Language", "en-US,en;q=0.9")).contains("Bạn cần đăng nhập."),
            "the browser language does not count");
    }

    @Test void theForbiddenBodyIsInTheLanguageOfTheDeviceOrTheAccount() throws Exception {
        assertTrue(body(as(get("/api/v1/manager/warranty-claims"), CUSTOMER)).contains("Bạn không có quyền"));
        assertTrue(body(as(get("/api/v1/manager/warranty-claims"), CUSTOMER).cookie(new Cookie("ebike_lang", "en"))).contains("You do not have permission"));

        // no cookie: the language saved on the account decides, even when the browser says Vietnamese
        User user = new User();
        user.setId(5L);
        user.setUsername("an");
        UserPreference english = new UserPreference();
        english.setLanguage("en");
        when(users.findByUsername("an")).thenReturn(Optional.of(user));
        when(preferences.findByUserId(5L)).thenReturn(Optional.of(english));
        String text = body(as(get("/api/v1/manager/warranty-claims"), CUSTOMER).header("Accept-Language", "vi-VN,vi;q=0.9"));
        assertTrue(text.contains("You do not have permission"), text);
        assertFalse(text.contains("Bạn không có quyền"));
    }

    @Test void anUnreadableBodyOrABadNumberAnswers400InTheRequestLanguage() throws Exception {
        MvcResult broken = mvc.perform(as(post("/api/v1/warranty/claims"), CUSTOMER).contentType(MediaType.APPLICATION_JSON).content("{not json")).andReturn();
        assertEquals(400, broken.getResponse().getStatus());
        assertTrue(broken.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("không hợp lệ"));

        MvcResult badPage = mvc.perform(as(get("/api/v1/manager/warranty-claims").param("page", "abc"), VIEW_QUEUE).cookie(new Cookie("ebike_lang", "en"))).andReturn();
        assertEquals(400, badPage.getResponse().getStatus());
        assertTrue(badPage.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("is not valid"));
    }
}
