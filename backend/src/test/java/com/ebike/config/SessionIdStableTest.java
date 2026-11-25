package com.ebike.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.service.product.ProductService;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * The session only holds the cart, and the sign-in is the JWT cookie. A signed-in visitor keeps the same session id from request to request:
 * if it changed on every request, two requests sent together with the old id would find an empty session and the cart would vanish.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SessionIdStableTest.TestConfiguration.class)
class SessionIdStableTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfiguration.class)
    static class TestConfiguration {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new SignedInFilter();
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
        ProductService productService() {
            return mock(ProductService.class);
        }
    }

    /** Signs a customer in on every request, the way the JWT filter does for a valid cookie. */
    static class SignedInFilter extends JwtAuthenticationFilter {
        SignedInFilter() {
            super(null, null, null);
        }

        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain)
            throws jakarta.servlet.ServletException, java.io.IOException {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("customer", "n/a", List.of()));
            chain.doFilter(request, response);
        }
    }

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
    }

    @Test void aSignedInVisitorKeepsTheSameSessionIdOnEveryRequest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/robots.txt").session(session));
            assertEquals(before, session.getId(), "request " + (i + 1) + " changed the session id");
        }
    }
}
