package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.auth.Role;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.security.filter.JwtAuthenticationFilter;
import com.ebike.service.auth.JwtTokenProvider;
import com.ebike.service.auth.PermissionService;
import jakarta.servlet.http.Cookie;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwt;
    private UserRepository users;
    private PermissionService permissions;
    private JwtAuthenticationFilter filter;
    private User alice;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwt = mock(JwtTokenProvider.class);
        users = mock(UserRepository.class);
        permissions = mock(PermissionService.class);
        filter = new JwtAuthenticationFilter(jwt, users, permissions);
        alice = Fx.user(1, "alice");
        Role r = new Role();
        r.setName(" customer ");
        Role blank = new Role();
        blank.setName("  ");
        alice.setRoles(new HashSet<>(Set.of(r, blank)));
        when(jwt.validateToken("good")).thenReturn(true);
        when(jwt.extractUsername("good")).thenReturn("alice");
        when(users.findWithPermissionsByUsername("alice")).thenReturn(Optional.of(alice));
        when(permissions.resolvePermissionCodes(alice)).thenReturn(Set.of("PROFILE_VIEW", " "));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest req(String path) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        r.setRequestURI(path);
        return r;
    }

    private MockFilterChain run(MockHttpServletRequest r, MockHttpServletResponse resp) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(r, resp, chain);
        return chain;
    }

    private static boolean passed(MockFilterChain c) {
        return c.getRequest() != null;
    }

    @Test
    void noTokenPassesAnonymously() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(passed(run(req("/api/v1/products"), resp))).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void bearerAuthenticatesWithRolesAndPermissions() throws Exception {
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = run(r, resp);
        assertThat(passed(chain)).isTrue();
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        assertThat(a.getName()).isEqualTo("alice");
        assertThat(a.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
            .containsExactlyInAnyOrder("ROLE_CUSTOMER", "PROFILE_VIEW");
    }

    @Test
    void cookieAuthenticatesToo() throws Exception {
        MockHttpServletRequest r = req("/customer/orders");
        r.setCookies(new Cookie("ebike_access_token", "good"));
        run(r, new MockHttpServletResponse());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
    }

    @Test
    void bearerWinsOverCookie() throws Exception {
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer good");
        r.setCookies(new Cookie("ebike_access_token", "garbage"));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        run(r, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void invalidBearerIs401WithJsonAndChainStops() throws Exception {
        when(jwt.validateToken("bad")).thenReturn(false);
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = run(r, resp);
        assertThat(passed(chain)).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentType()).startsWith("application/json");
        assertThat(resp.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("message");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void emptyBearerIs401() throws Exception {
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        run(r, resp);
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void staleCookieIsDroppedNotRejected() throws Exception {
        when(jwt.validateToken("stale")).thenReturn(false);
        MockHttpServletRequest r = req("/login");
        r.setCookies(new Cookie("ebike_access_token", "stale"));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = run(r, resp);
        assertThat(passed(chain)).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getHeader("Set-Cookie")).contains("ebike_access_token=").contains("Max-Age=0").contains("HttpOnly");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void lockedUserWithCookieIsDroppedNotRejected() throws Exception {
        alice.setActive(false);
        MockHttpServletRequest r = req("/shop");
        r.setCookies(new Cookie("ebike_access_token", "good"));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = run(r, resp);
        assertThat(passed(chain)).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void lockedUserWithBearerIs401() throws Exception {
        alice.setActive(false);
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(passed(run(r, resp))).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void nullActiveFlagIsTreatedAsInactive() throws Exception {
        alice.setActive(null);
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        run(r, resp);
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void deletedUserIsRejected() throws Exception {
        when(users.findWithPermissionsByUsername("alice")).thenReturn(Optional.empty());
        when(users.findWithPermissionsByEmail("alice")).thenReturn(Optional.empty());
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        run(r, resp);
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void emailInTokenSubjectFallsBackToEmailLookupLowercased() throws Exception {
        when(jwt.validateToken("mail")).thenReturn(true);
        when(jwt.extractUsername("mail")).thenReturn(" Alice@Example.COM ");
        when(users.findWithPermissionsByUsername("Alice@Example.COM")).thenReturn(Optional.empty());
        when(users.findWithPermissionsByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer mail");
        run(r, new MockHttpServletResponse());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
    }

    @Test
    void blankSubjectIsRejected() throws Exception {
        when(jwt.validateToken("nosub")).thenReturn(true);
        when(jwt.extractUsername("nosub")).thenReturn(null);
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer nosub");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        run(r, resp);
        assertThat(resp.getStatus()).isEqualTo(401);
        verify(users, never()).findWithPermissionsByUsername(anyString());
    }

    @Test
    void loginAndRegisterSkipTokenCheckEvenWithGarbageToken() throws Exception {
        MockHttpServletRequest r = req("/api/v1/auth/login");
        r.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(passed(run(r, resp))).isTrue();
        assertThat(resp.getStatus()).isEqualTo(200);
        MockHttpServletRequest r2 = req("/api/v1/auth/register");
        r2.setCookies(new Cookie("ebike_access_token", "bad"));
        assertThat(passed(run(r2, new MockHttpServletResponse()))).isTrue();
    }

    @Test
    void blankCookieValueIsIgnored() throws Exception {
        MockHttpServletRequest r = req("/shop");
        r.setCookies(new Cookie("ebike_access_token", "  "));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertThat(passed(run(r, resp))).isTrue();
        assertThat(resp.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void otherCookiesAreNotTokens() throws Exception {
        MockHttpServletRequest r = req("/shop");
        r.setCookies(new Cookie("session", "good"));
        run(r, new MockHttpServletResponse());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void lowercaseBearerSchemeFallsBackToCookieNotBearer() throws Exception {
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "bearer good");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = run(r, resp);
        assertThat(passed(chain)).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void staleSecurityContextIsClearedOnRejectedToken() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("someone", null, List.of()));
        when(jwt.validateToken("bad")).thenReturn(false);
        MockHttpServletRequest r = req("/api/v1/orders");
        r.addHeader("Authorization", "Bearer bad");
        run(r, new MockHttpServletResponse());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
