package com.ebike.controller.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ebike.config.SecurityConfiguration;
import com.ebike.config.UserLocaleResolver;
import com.ebike.dto.product.response.CategorySummaryDto;
import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.service.product.ProductService;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** V35: the real security rules in front of /sitemap.xml and /robots.txt: crawlers have no account, so both are open to everyone. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SeoSecurityTest.TestConfiguration.class)
class SeoSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfiguration.class)
    static class TestConfiguration {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new NoSignInFilter();
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
        ProductService productService() {
            ProductService service = mock(ProductService.class);
            when(service.getProducts(null, null, null, null, null, null, null, null, null, null, null, null)).thenReturn(List.of(
                new ProductSummaryDto(1L, "Kinetic X", "kinetic-x", "d", BigDecimal.TEN, null, null, 0, 1, false, new CategorySummaryDto(1, "c", "c"), List.of())));
            return service;
        }

        @Bean
        SeoController seoController(ProductService service) {
            return new SeoController(service, "https://shop.example.vn");
        }
    }

    /** Never signs anyone in: a crawler is a visitor. */
    static class NoSignInFilter extends JwtAuthenticationFilter {
        NoSignInFilter() {
            super(null, null, null);
        }

        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain)
            throws jakarta.servlet.ServletException, java.io.IOException {
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

    @Test void aCrawlerWithoutAnAccountReadsTheSitemap() throws Exception {
        MvcResult result = mvc.perform(get("/sitemap.xml")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("<loc>https://shop.example.vn/product/kinetic-x</loc>"));
    }

    @Test void aCrawlerWithoutAnAccountReadsRobotsTxt() throws Exception {
        MvcResult result = mvc.perform(get("/robots.txt")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("Sitemap: https://shop.example.vn/sitemap.xml"));
    }

    @Test void theApiStaysBehindItsPermissions() throws Exception {
        assertEquals(401, mvc.perform(get("/api/v1/favorites")).andReturn().getResponse().getStatus());
    }
}
