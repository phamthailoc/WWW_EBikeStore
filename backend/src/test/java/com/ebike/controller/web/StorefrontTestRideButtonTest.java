package com.ebike.controller.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.dto.product.response.ProductDetailDto;
import com.ebike.dto.product.response.ProductSpecificationDto;
import com.ebike.service.product.ProductService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ConcurrentModel;

/** V23: who is offered "Book a test ride" on the bike page: visitors (sent to sign in) and customers, for a bike that can be ridden. */
class StorefrontTestRideButtonTest {
    /** 23:30 on 21 November in Hanoi time, which is already 22 November in Tokyo and still 21 November in London: the day must be the showroom's. */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T16:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    ProductService products = mock(ProductService.class);
    StorefrontController controller = new StorefrontController(products, 14, CLOCK);

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
    }

    private ConcurrentModel detailAs(Authentication authentication, String vehicleType) {
        ProductDetailDto detail = mock(ProductDetailDto.class);
        ProductSpecificationDto specification = mock(ProductSpecificationDto.class);
        when(specification.vehicleType()).thenReturn(vehicleType);
        when(detail.specification()).thenReturn(vehicleType == null ? null : specification);
        when(products.getProductBySlug(any())).thenReturn(detail);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        ConcurrentModel model = new ConcurrentModel();
        controller.detail("kinetic-x", model);
        return model;
    }

    private static UsernamePasswordAuthenticationToken signedIn(String role) {
        return new UsernamePasswordAuthenticationToken("an", "x", AuthorityUtils.createAuthorityList("ROLE_" + role));
    }

    @Test void aVisitorSeesTheButton() {
        assertEquals(true, detailAs(new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")), "E_SCOOTER").getAttribute("canTestRide"));
        assertEquals(true, detailAs(null, "E_BIKE").getAttribute("canTestRide"));
    }

    @Test void aCustomerSeesTheButton() {
        assertEquals(true, detailAs(signedIn("CUSTOMER"), "E_MOTORBIKE").getAttribute("canTestRide"));
    }

    @Test void staffDoNotSeeTheButton() {
        for (String role : List.of("MANAGER", "ADMIN")) {
            assertEquals(false, detailAs(signedIn(role), "E_SCOOTER").getAttribute("canTestRide"), role);
        }
    }

    @Test void batteriesAndAccessoriesCannotBeRidden() {
        assertEquals(false, detailAs(signedIn("CUSTOMER"), "BATTERY").getAttribute("canTestRide"));
        assertEquals(false, detailAs(null, "ACCESSORY").getAttribute("canTestRide"));
    }

    @Test void theDialogLearnsHowManyDaysAreOpen() {
        assertEquals(14, detailAs(signedIn("CUSTOMER"), "E_BIKE").getAttribute("testRideDays"));
        controller = new StorefrontController(products, 21, CLOCK);
        assertEquals(21, detailAs(signedIn("CUSTOMER"), "E_BIKE").getAttribute("testRideDays"));
    }

    @Test void theDialogStartsOnTheShowroomsDay() {
        assertEquals("2026-11-21", detailAs(signedIn("CUSTOMER"), "E_BIKE").getAttribute("testRideToday"));
        controller = new StorefrontController(products, 14, Clock.fixed(Instant.parse("2026-11-21T17:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        assertEquals("2026-11-22", detailAs(signedIn("CUSTOMER"), "E_BIKE").getAttribute("testRideToday"));
    }

    @Test void aProductWithNoSpecificationIsNotOfferedForARide() {
        assertEquals(false, detailAs(signedIn("CUSTOMER"), null).getAttribute("canTestRide"));
    }
}
