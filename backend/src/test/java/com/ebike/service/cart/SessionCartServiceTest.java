package com.ebike.service.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.dto.cart.CartLine;
import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.product.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** The cart kept in the HTTP session: shape checks, dropped products, and what an order takes out of it. */
class SessionCartServiceTest {
    ProductRepository products = mock(ProductRepository.class);
    SessionCartService cart = new SessionCartService(products);
    MockHttpSession session = new MockHttpSession();

    @BeforeEach void setup() {
        when(products.findById(anyLong())).thenReturn(Optional.empty());
        for (long id : new long[] {1L, 2L}) {
            Product product = new Product();
            ReflectionTestUtils.setField(product, "id", id);
            product.setSlug("xe-" + id);
            product.setActive(true);
            when(products.findById(id)).thenReturn(Optional.of(product));
        }
    }

    @Test void aCartStartsEmptyAndKeepsWhatWasPut() {
        assertEquals(List.of(), cart.read(session));
        cart.replace(session, List.of(new CartLine(1L, null, 2, "ignored")));
        assertEquals(List.of(new CartLine(1L, null, 2, "xe-1")), cart.read(session));
    }

    @Test void aBikeThatDoesNotExistIsDropped() {
        var kept = cart.replace(session, List.of(new CartLine(1L, null, 1, null), new CartLine(99L, null, 1, null)));
        assertEquals(1, kept.size());
    }

    @Test void badQuantitiesDuplicatesAndTooManyLinesAreRefused() {
        assertThrows(ResponseStatusException.class, () -> cart.replace(session, List.of(new CartLine(1L, null, 0, null))));
        assertThrows(ResponseStatusException.class, () -> cart.replace(session, List.of(new CartLine(1L, null, 11, null))));
        assertThrows(ResponseStatusException.class, () -> cart.replace(session,
            List.of(new CartLine(1L, null, 1, null), new CartLine(1L, null, 2, null))));
        List<CartLine> many = java.util.stream.LongStream.rangeClosed(1, 21).mapToObj(i -> new CartLine(i, null, 1, null)).toList();
        assertThrows(ResponseStatusException.class, () -> cart.replace(session, many));
    }

    @Test void anOrderTakesTheBoughtQuantitiesOutOfTheCart() {
        cart.replace(session, List.of(new CartLine(1L, null, 3, null), new CartLine(2L, null, 1, null)));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(1L, 1, null), new OrderCreateItemRequest(2L, 1, null)));
        assertEquals(List.of(new CartLine(1L, null, 2, "xe-1")), cart.read(session));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(1L, 2, null)));
        assertEquals(List.of(), cart.read(session));
    }

    @Test void aBuyNowOrderOfSomethingNotInTheCartLeavesItAlone() {
        cart.replace(session, List.of(new CartLine(1L, null, 1, null)));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(2L, 1, null)));
        assertEquals(1, cart.read(session).size());
    }
    @Test void aColourThatIsNotThisBikesIsDropped() {
        assertEquals(0, cart.replace(session, List.of(new CartLine(1L, 77L, 1, null))).size());
    }

    @Test void onlyTheBoughtColourLeavesTheCart() {
        var bike = products.findById(1L).get();
        var red = new com.ebike.model.entity.product.ProductVariant();
        ReflectionTestUtils.setField(red, "id", 5L);
        var blue = new com.ebike.model.entity.product.ProductVariant();
        ReflectionTestUtils.setField(blue, "id", 6L);
        bike.setVariants(new java.util.HashSet<>(java.util.Set.of(red, blue)));
        cart.replace(session, List.of(new CartLine(1L, 5L, 2, null), new CartLine(1L, 6L, 1, null)));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(1L, 2, 5L)));
        assertEquals(List.of(new CartLine(1L, 6L, 1, "xe-1")), cart.read(session));
    }
}
