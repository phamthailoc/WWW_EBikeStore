package com.ebike.controller.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.config.order.VnPayProperties;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.ui.ConcurrentModel;

/** V23: the schedule page tells its script whether the account may change a test ride, so view-only staff are not shown buttons that end in a 403. */
class WorkspaceTestRideAccessTest {

    private ConcurrentModel open(String... authorities) {
        MessageSource messages = mock(MessageSource.class);
        when(messages.getMessage(anyString(), any(), anyString(), any(Locale.class))).thenAnswer(call -> call.getArgument(2));
        WorkspaceController controller = new WorkspaceController(mock(VnPayProperties.class), messages);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/manager/test-rides");
        Authentication authentication = new UsernamePasswordAuthenticationToken("staff", "n/a", AuthorityUtils.createAuthorityList(authorities));
        ConcurrentModel model = new ConcurrentModel();
        assertEquals("store/workspace", controller.workspace(request, model, authentication));
        return model;
    }

    @Test
    void staffWhoMayUpdateOrdersAreOfferedTheSteps() {
        assertEquals(true, open(PermissionConstants.OrderManagement.ORDER_VIEW_ALL, PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS).getAttribute("canUpdateOrders"));
    }

    @Test
    void staffWhoMayOnlyViewAreNot() {
        assertEquals(false, open(PermissionConstants.OrderManagement.ORDER_VIEW_ALL).getAttribute("canUpdateOrders"));
    }
}
