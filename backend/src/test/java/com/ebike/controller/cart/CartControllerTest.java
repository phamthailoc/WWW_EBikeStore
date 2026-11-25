package com.ebike.controller.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ebike.service.cart.SessionCartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

/** The cart endpoint refuses a body that is too big or not a cart before anything is kept. */
class CartControllerTest {
    SessionCartService service = mock(SessionCartService.class);
    CartController controller = new CartController(service, new ObjectMapper());

    private MockHttpServletRequest put(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/cart");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test void aSmallCartIsPassedOn() throws Exception {
        MockHttpServletRequest request = put("[{\"id\":1,\"variantId\":null,\"quantity\":2}]");
        controller.replace(request);
        verify(service).replace(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test void aBodyOverEightKilobytesIsRefusedWithoutParsing() {
        String big = "[" + "{\"id\":1,\"quantity\":1,\"slug\":\"" + "x".repeat(9000) + "\"}" + "]";
        var ex = assertThrows(ResponseStatusException.class, () -> controller.replace(put(big)));
        assertEquals(413, ex.getStatusCode().value());
        verify(service, never()).replace(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test void somethingThatIsNotACartIsABadRequest() {
        var ex = assertThrows(ResponseStatusException.class, () -> controller.replace(put("{\"not\":\"a list\"}")));
        assertEquals(400, ex.getStatusCode().value());
    }
}
