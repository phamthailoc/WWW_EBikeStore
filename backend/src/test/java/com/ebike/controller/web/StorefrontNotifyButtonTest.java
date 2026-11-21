package com.ebike.controller.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.ebike.service.product.ProductService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ConcurrentModel;

/** V22: who is offered the "Notify me when back in stock" button on the bike page, and who has a waiting list to load. */
class StorefrontNotifyButtonTest {
    StorefrontController controller = new StorefrontController(mock(ProductService.class));

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
    }

    private ConcurrentModel detailAs(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        ConcurrentModel model = new ConcurrentModel();
        controller.detail("kinetic-x", model);
        return model;
    }

    private static UsernamePasswordAuthenticationToken signedIn(String role) {
        return new UsernamePasswordAuthenticationToken("an", "x", AuthorityUtils.createAuthorityList("ROLE_" + role));
    }

    @Test void aVisitorSeesTheButtonButHasNoListToLoad() {
        ConcurrentModel model = detailAs(new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertEquals(true, model.getAttribute("canNotifyStock"));
        assertEquals(false, model.getAttribute("stockAlertCustomer"));
    }

    @Test void noAuthenticationAtAllCountsAsAVisitor() {
        ConcurrentModel model = detailAs(null);
        assertEquals(true, model.getAttribute("canNotifyStock"));
        assertEquals(false, model.getAttribute("stockAlertCustomer"));
    }

    @Test void aCustomerSeesTheButtonAndLoadsTheList() {
        ConcurrentModel model = detailAs(signedIn("CUSTOMER"));
        assertEquals(true, model.getAttribute("canNotifyStock"));
        assertEquals(true, model.getAttribute("stockAlertCustomer"));
    }

    @Test void managerAndAdminDoNotSeeTheButton() {
        for (String role : List.of("MANAGER", "ADMIN")) {
            ConcurrentModel model = detailAs(signedIn(role));
            assertEquals(false, model.getAttribute("canNotifyStock"), role);
            assertEquals(false, model.getAttribute("stockAlertCustomer"), role);
        }
    }
}
