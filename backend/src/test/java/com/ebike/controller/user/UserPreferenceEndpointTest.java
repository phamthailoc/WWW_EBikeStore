package com.ebike.controller.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.service.user.UserProfileService;
import com.ebike.service.user.UserProfileService.UserPreferenceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The profile page reads the notification switch with a plain GET, without a request body. */
class UserPreferenceEndpointTest {

    @Test
    void preferencesCanBeReadWithAGet() throws Exception {
        UserProfileService service = mock(UserProfileService.class);
        when(service.getPreferences(eq(9L), any())).thenReturn(new UserPreferenceResponse(9L, "light", "vi", false));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();

        mvc.perform(get("/api/v1/users/9/preferences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationsEnabled").value(false));
        verify(service).getPreferences(eq(9L), any());
    }
}
