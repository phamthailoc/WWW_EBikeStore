package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.common.exception.ApiExceptionHandler;
import com.ebike.controller.auth.AuthController;
import com.ebike.dto.auth.request.ChangePasswordRequest;
import com.ebike.dto.auth.request.DeleteOwnAccountRequest;
import com.ebike.dto.auth.request.RegisterRequest;
import com.ebike.dto.auth.response.AuthResponse;
import com.ebike.dto.auth.response.UserProfileResponse;
import com.ebike.service.auth.AuthenticationService;
import com.ebike.service.user.OwnAccountService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerTest {

    AuthenticationService auth = mock(AuthenticationService.class);
    OwnAccountService own = mock(OwnAccountService.class);
    AuthController controller;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new AuthController(auth, own);
        ReflectionTestUtils.setField(controller, "jwtExpirationMs", 3_600_000L);
        ReflectionTestUtils.setField(controller, "secureCookie", true);
        ReflectionTestUtils.setField(controller, "sameSite", "Strict");
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private static final AuthResponse OK = new AuthResponse(1L, "tien", "t@x.com", "Tien", "tok-123", Set.of("CUSTOMER"));

    @Test
    void register_returns201WithBody_andNoAuthCookie() throws Exception {
        when(auth.register(any(RegisterRequest.class))).thenReturn(OK);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"tien\",\"email\":\"t@x.com\",\"password\":\"secret1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("tien"));
    }

    @Test
    void register_serviceConflict_isMappedToApiError409() throws Exception {
        when(auth.register(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Email đã được sử dụng."));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email đã được sử dụng."))
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void register_malformedJson_is400NotAStackTrace() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Dữ liệu gửi lên không hợp lệ."));
        verify(auth, never()).register(any());
    }

    @Test
    void login_setsHttpOnlySecureSameSiteCookieWithConfiguredLifetime() throws Exception {
        when(auth.login(any(), any(), any())).thenReturn(OK);
        var res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "JUnit")
                .content("{\"usernameOrEmail\":\"tien\",\"password\":\"secret1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("tok-123"))
            .andReturn().getResponse();
        String cookie = res.getHeader("Set-Cookie");
        assertThat(cookie).contains("ebike_access_token=tok-123").contains("HttpOnly").contains("Secure")
            .contains("SameSite=Strict").contains("Path=/").contains("Max-Age=3600");
        verify(auth).login(any(), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    void login_failure_setsNoCookie() throws Exception {
        when(auth.login(any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "x"));
        var res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"tien\",\"password\":\"bad\"}"))
            .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(res.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void login_changesAnExistingSessionId_toPreventFixation() throws Exception {
        when(auth.login(any(), any(), any())).thenReturn(OK);
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();
        var req = post("/api/v1/auth/login").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("{\"usernameOrEmail\":\"tien\",\"password\":\"secret1\"}");
        mvc.perform(req).andExpect(status().isOk());
        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getId()).isNotEqualTo(before);
    }

    @Test
    void logout_clearsCookie_andInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var res = mvc.perform(post("/api/v1/auth/logout").session(session))
            .andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(res.getHeader("Set-Cookie")).contains("ebike_access_token=;").contains("Max-Age=0").contains("HttpOnly");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void logout_withoutSession_stillClearsCookie() throws Exception {
        var res = mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(res.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    void profile_alwaysReadsTheSignedInAccount_notAParameter() throws Exception {
        when(auth.getProfile("tien")).thenReturn(new UserProfileResponse(1L, "tien", "t@x.com", "A", "B", true, true, Set.of("CUSTOMER"), Set.of()));
        mvc.perform(get("/api/v1/auth/profile").param("usernameOrEmail", "victim").principal(Fx.auth("tien")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("tien"));
        verify(auth).getProfile("tien");
        verify(auth, never()).getProfile("victim");
    }

    @Test
    void updateProfile_withoutAuthentication_is401() throws Exception {
        mvc.perform(put("/api/v1/auth/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        verify(auth, never()).updateProfile(any(), any());
    }

    @Test
    void updateProfile_usesPrincipalName() throws Exception {
        mvc.perform(put("/api/v1/auth/profile").principal(Fx.auth("tien")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\"A\"}")).andExpect(status().isOk());
        verify(auth).updateProfile(eq("tien"), any());
    }

    @Test
    void changePassword_204_andUsesPrincipal() throws Exception {
        mvc.perform(post("/api/v1/auth/change-password").principal(Fx.auth("tien")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\",\"confirmPassword\":\"b\"}"))
            .andExpect(status().isNoContent());
        verify(auth).changePassword(eq("tien"), any(ChangePasswordRequest.class));
    }

    @Test
    void changePassword_withoutAuthentication_is401() throws Exception {
        mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        verify(auth, never()).changePassword(any(), any());
    }

    @Test
    void deleteOwnAccount_204_clearsCookie_andInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var res = mvc.perform(delete("/api/v1/auth/account").principal(Fx.auth("tien")).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\",\"confirmEmail\":\"t@x.com\"}"))
            .andExpect(status().isNoContent()).andReturn().getResponse();
        verify(own).deleteOwnAccount(any(), any(DeleteOwnAccountRequest.class));
        assertThat(res.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void deleteOwnAccount_refused_keepsCookieAndSession() throws Exception {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu không đúng."))
            .when(own).deleteOwnAccount(any(), any());
        MockHttpSession session = new MockHttpSession();
        var res = mvc.perform(delete("/api/v1/auth/account").principal(Fx.auth("tien")).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"bad\",\"confirmEmail\":\"t@x.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(header().doesNotExist("Set-Cookie")).andReturn().getResponse();
        assertThat(session.isInvalid()).isFalse();
        assertThat(res.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Mật khẩu không đúng.");
    }
}
