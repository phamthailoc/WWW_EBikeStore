package com.ebike.config;

import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.common.constants.PermissionConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final String[] ADMIN_CONSOLE_ROLES = {"ADMIN", "OPERATOR", "SUPPORT", "MAINTENANCE"};

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .anonymous(anonymous -> anonymous.authorities(
                PermissionConstants.Guest.PRODUCT_VIEW,
                PermissionConstants.Guest.PRODUCT_SEARCH,
                PermissionConstants.Guest.CATEGORY_VIEW,
                PermissionConstants.Guest.REVIEW_VIEW,
                PermissionConstants.Guest.ORDER_CREATE,
                PermissionConstants.Guest.PAYMENT_CREATE,
                PermissionConstants.ChatbotManagement.CHATBOT_USE
            ))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/", "/shop", "/products", "/models", "/bike/**", "/product/**", "/models/**", "/login", "/auth", "/signup", "/checkout", "/checkout/success", "/payment/return", "/support", "/compare", "/chatbot", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/favorites").authenticated()
                .requestMatchers("/customer/**").hasAnyAuthority(PermissionConstants.Customer.PROFILE_VIEW, PermissionConstants.OrderManagement.ORDER_VIEW_ALL)
                .requestMatchers("/manager/**").hasAuthority(PermissionConstants.OrderManagement.ORDER_VIEW_ALL)
                .requestMatchers("/admin/**").hasAnyRole(ADMIN_CONSOLE_ROLES)
                .requestMatchers(HttpMethod.POST, "/api/v1/orders/quote").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/orders/email-verification/send", "/api/v1/orders/email-verification/verify").permitAll()
                .requestMatchers("/api/v1/payments/vnpay/ipn", "/api/v1/payments/vnpay/return").permitAll()
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/login/**",
                    "/api/v1/auth/logout",
                    "/api/v1/auth/session",
                    "/api/v1/health/**",
                    "/api/v1/media/**",
                    "/error"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").hasAuthority(PermissionConstants.Guest.PRODUCT_VIEW)
                .requestMatchers(HttpMethod.POST, "/api/v1/products/*/reviews").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/v1/reviews/*").hasAnyAuthority(
                    PermissionConstants.Customer.REVIEW_CREATE,
                    PermissionConstants.ProductManagement.REVIEW_MODERATE
                )
                .requestMatchers(HttpMethod.DELETE, "/api/v1/reviews/*").hasAnyAuthority(
                    PermissionConstants.Customer.REVIEW_CREATE,
                    PermissionConstants.ProductManagement.REVIEW_MODERATE
                )
                .requestMatchers(HttpMethod.GET, "/api/v1/showrooms").hasAuthority(PermissionConstants.Guest.PRODUCT_VIEW)
                .requestMatchers(HttpMethod.POST, "/api/v1/chatbot/ask").hasAuthority(PermissionConstants.ChatbotManagement.CHATBOT_USE)
                .requestMatchers(HttpMethod.POST, "/api/v1/chatbot/debug").hasAuthority(PermissionConstants.ChatbotManagement.CHATBOT_CONFIGURE)
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/profile").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/v1/auth/profile").authenticated()
                .requestMatchers("/api/v1/auth/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/*").hasAnyAuthority(
                    PermissionConstants.Customer.ORDER_VIEW_OWN,
                    PermissionConstants.OrderManagement.ORDER_VIEW_ALL
                )
                .requestMatchers(HttpMethod.POST, "/api/v1/orders").hasAuthority(PermissionConstants.Guest.ORDER_CREATE)
                .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/cancellation-request").hasAuthority(PermissionConstants.Customer.ORDER_CANCEL_OWN)
                .requestMatchers(HttpMethod.PATCH, "/api/v1/orders/*/status").hasAuthority(PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS)
                .requestMatchers(HttpMethod.GET, "/api/v1/favorites").hasAuthority(PermissionConstants.Customer.FAVORITE_VIEW)
                .requestMatchers(HttpMethod.POST, "/api/v1/favorites").hasAuthority(PermissionConstants.Customer.FAVORITE_UPDATE)
                .requestMatchers(HttpMethod.DELETE, "/api/v1/favorites/*").hasAuthority(PermissionConstants.Customer.FAVORITE_UPDATE)
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications", "/api/v1/notifications/unread-count", "/api/v1/notifications/stream").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/notifications/*/read", "/api/v1/notifications/read-all").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/history").hasAnyAuthority(
                    PermissionConstants.Customer.PAYMENT_VIEW_OWN,
                    PermissionConstants.Customer.ORDER_VIEW_OWN,
                    PermissionConstants.OrderManagement.ORDER_VIEW_ALL
                )
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/vnpay/create").hasAuthority(PermissionConstants.Guest.PAYMENT_CREATE)
                .requestMatchers(HttpMethod.GET, "/api/v1/users/**").hasAnyAuthority(
                    PermissionConstants.Customer.PROFILE_VIEW,
                    PermissionConstants.UserManagement.USER_VIEW
                )
                .requestMatchers(HttpMethod.POST, "/api/v1/users/**").hasAuthority(PermissionConstants.Customer.PROFILE_UPDATE)
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/**").hasAuthority(PermissionConstants.Customer.PROFILE_UPDATE)
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/admin/overview",
                    "/api/v1/admin/pricing-rules",
                    "/api/v1/admin/promotions",
                    "/api/v1/admin/accounts",
                    "/api/v1/admin/roles",
                    "/api/v1/admin/audit-logs"
                ).hasAnyRole(ADMIN_CONSOLE_ROLES)
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/pricing-rules/*").hasAnyRole(ADMIN_CONSOLE_ROLES)
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/promotions").hasAnyRole(ADMIN_CONSOLE_ROLES)
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/promotions/*").hasAnyRole(ADMIN_CONSOLE_ROLES)
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/accounts").hasAnyRole(ADMIN_CONSOLE_ROLES)
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/accounts/*/role").hasAnyRole(ADMIN_CONSOLE_ROLES)
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/accounts/*/status").hasAnyRole(ADMIN_CONSOLE_ROLES)
                .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/accounts/*").hasAnyRole(ADMIN_CONSOLE_ROLES)
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/product-images").hasAuthority(PermissionConstants.ProductManagement.PRODUCT_CREATE)
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/product-images/**").hasAuthority(PermissionConstants.ProductManagement.PRODUCT_UPDATE)
                .requestMatchers(HttpMethod.PUT, "/api/v1/admin/product-images/**").hasAuthority(PermissionConstants.ProductManagement.PRODUCT_UPDATE)
                .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/product-images/**").hasAuthority(PermissionConstants.ProductManagement.PRODUCT_DELETE)
                .requestMatchers("/api/v1/customer/**").hasAnyAuthority(
                    PermissionConstants.Customer.PROFILE_VIEW,
                    PermissionConstants.OrderManagement.ORDER_VIEW_ALL
                )
                .requestMatchers("/api/v1/manager/**").hasAuthority(PermissionConstants.OrderManagement.ORDER_VIEW_ALL)
                .requestMatchers("/api/v1/admin/**").hasAuthority(PermissionConstants.AccessControl.PERMISSION_MANAGE)
                .anyRequest().denyAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/v1/")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Unauthorized\"}");
                    } else {
                        response.sendRedirect("/login?returnTo=" + java.net.URLEncoder.encode(request.getRequestURI(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
