package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ebike.dto.cart.CartLine;
import com.ebike.dto.order.request.OrderCreateItemRequest;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.cart.SessionCartService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionCartServiceTest {

    static final String KEY = "kinetic.cart";

    @Mock ProductRepository products;
    SessionCartService cart;
    MockHttpSession session;
    Product bike;

    @BeforeEach
    void setUp() {
        cart = new SessionCartService(products);
        session = new MockHttpSession();
        bike = OrderFixtures.bike(1L, "Kinetic X1", "10000000", 5, VehicleType.E_BIKE);
        bike.setSlug("kinetic-x1");
        when(products.findById(1L)).thenReturn(Optional.of(bike));
    }

    private static CartLine line(Long id, Long variant, Integer qty) {
        return new CartLine(id, variant, qty, "client-slug");
    }

    private static void assertBad(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- read ----------------------------------------------------------------------------------------

    @Test
    void readWithoutASessionOrCartIsEmpty() {
        assertThat(cart.read(null)).isEmpty();
        assertThat(cart.read(session)).isEmpty();
    }

    @Test
    void readIgnoresAnAttributeOfTheWrongType() {
        session.setAttribute(KEY, "not a list");
        assertThat(cart.read(session)).isEmpty();
    }

    @Test
    void readReturnsAnUnmodifiableCopy() {
        List<CartLine> stored = new ArrayList<>(List.of(line(1L, null, 2)));
        session.setAttribute(KEY, stored);
        List<CartLine> read = cart.read(session);
        assertThatThrownBy(() -> read.add(line(1L, null, 1))).isInstanceOf(UnsupportedOperationException.class);
        stored.clear();
        assertThat(read).hasSize(1);
    }

    // --- replace -------------------------------------------------------------------------------------

    @Test
    void replaceKeepsOnlyIdVariantQuantityAndTakesTheSlugFromTheCatalogue() {
        List<CartLine> result = cart.replace(session, List.of(line(1L, null, 3)));
        assertThat(result).containsExactly(new CartLine(1L, null, 3, "kinetic-x1"));
        assertThat(cart.read(session)).isEqualTo(result);
    }

    @Test
    void replaceWithNullOrEmptyClearsTheCart() {
        cart.replace(session, List.of(line(1L, null, 1)));
        assertThat(cart.replace(session, null)).isEmpty();
        assertThat(cart.read(session)).isEmpty();
        cart.replace(session, List.of(line(1L, null, 1)));
        assertThat(cart.replace(session, List.of())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void quantityOutsideOneToTenIsRejected(int qty) {
        assertBad(() -> cart.replace(session, List.of(line(1L, null, qty))));
        assertThat(cart.read(session)).isEmpty();
    }

    @Test
    void quantityOneAndTenAreAccepted() {
        assertThat(cart.replace(session, List.of(line(1L, null, 1)))).hasSize(1);
        assertThat(cart.replace(session, List.of(line(1L, null, 10)))).hasSize(1);
    }

    @Test
    void nullLineNullIdAndNullQuantityAreRejected() {
        assertBad(() -> cart.replace(session, java.util.Collections.singletonList(null)));
        assertBad(() -> cart.replace(session, List.of(line(null, null, 1))));
        assertBad(() -> cart.replace(session, List.of(line(1L, null, null))));
    }

    @Test
    void aFailedReplaceLeavesTheOldCartUntouched() {
        cart.replace(session, List.of(line(1L, null, 2)));
        assertBad(() -> cart.replace(session, List.of(line(1L, null, 99))));
        assertThat(cart.read(session)).extracting(CartLine::quantity).containsExactly(2);
    }

    @Test
    void theSameBikeAndColourTwiceIsRejectedButTwoColoursAreFine() {
        assertBad(() -> cart.replace(session, List.of(line(1L, null, 1), line(1L, null, 2))));
        OrderFixtures.colour(11L, bike, "Red", 3, null);
        OrderFixtures.colour(12L, bike, "Blue", 3, null);
        assertThat(cart.replace(session, List.of(line(1L, 11L, 1), line(1L, 12L, 1)))).hasSize(2);
    }

    @Test
    void twentyLinesAreAllowedAndTwentyOneAreNot() {
        for (long id = 1; id <= 21; id++) {
            Product p = OrderFixtures.bike(id, "B" + id, "1", 1, VehicleType.E_BIKE);
            when(products.findById(id)).thenReturn(Optional.of(p));
        }
        List<CartLine> twenty = IntStream.rangeClosed(1, 20).mapToObj(i -> line((long) i, null, 1)).toList();
        assertThat(cart.replace(session, twenty)).hasSize(20);
        List<CartLine> twentyOne = IntStream.rangeClosed(1, 21).mapToObj(i -> line((long) i, null, 1)).toList();
        assertBad(() -> cart.replace(session, twentyOne));
    }

    @Test
    void aBikeThatNoLongerExistsIsDroppedSilently() {
        when(products.findById(404L)).thenReturn(Optional.empty());
        assertThat(cart.replace(session, List.of(line(404L, null, 1), line(1L, null, 1)))).extracting(CartLine::id).containsExactly(1L);
    }

    @Test
    void aColourOfAnotherBikeIsDropped() {
        assertThat(cart.replace(session, List.of(line(1L, 999L, 1)))).isEmpty();
    }

    @Test
    void aSwitchedOffBikeStaysSoThePageCanSayItIsUnavailable() {
        bike.setActive(false);
        assertThat(cart.replace(session, List.of(line(1L, null, 1)))).hasSize(1);
    }

    @Test
    void replaceWithoutASessionStillValidatesAndReturnsTheLines() {
        assertThat(cart.replace(null, List.of(line(1L, null, 1)))).hasSize(1);
        assertBad(() -> cart.replace(null, List.of(line(1L, null, 0))));
    }

    @Test
    void theStoredCartIsACopyNotTheCallersList() {
        List<CartLine> requested = new ArrayList<>(List.of(line(1L, null, 1)));
        cart.replace(session, requested);
        requested.clear();
        assertThat(cart.read(session)).hasSize(1);
    }

    // --- clear / removePurchased ---------------------------------------------------------------------

    @Test
    void clearRemovesTheCartAndToleratesNoSession() {
        cart.replace(session, List.of(line(1L, null, 1)));
        cart.clear(session);
        assertThat(session.getAttribute(KEY)).isNull();
        cart.clear(null);
    }

    @Test
    void purchasedQuantitiesLeaveTheCartAndTheRestStays() {
        session.setAttribute(KEY, new ArrayList<>(List.of(line(1L, null, 5), line(2L, null, 1))));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(1L, 2, null)));
        assertThat(cart.read(session)).extracting(CartLine::id, CartLine::quantity).containsExactly(
            org.assertj.core.groups.Tuple.tuple(1L, 3), org.assertj.core.groups.Tuple.tuple(2L, 1));
    }

    @Test
    void buyingTheWholeLineRemovesItAndAnEmptyCartRemovesTheAttribute() {
        session.setAttribute(KEY, new ArrayList<>(List.of(line(1L, null, 2))));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(1L, 2, null)));
        assertThat(session.getAttribute(KEY)).isNull();
    }

    @Test
    void buyingMoreThanTheCartHeldRemovesTheLineNeverGoesNegative() {
        session.setAttribute(KEY, new ArrayList<>(List.of(line(1L, null, 2))));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(1L, 9, null)));
        assertThat(cart.read(session)).isEmpty();
    }

    @Test
    void aDifferentColourIsNotRemoved() {
        session.setAttribute(KEY, new ArrayList<>(List.of(line(1L, 11L, 2))));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(1L, 2, 12L)));
        assertThat(cart.read(session)).hasSize(1);
    }

    @Test
    void buyNowOfAnItemNeverInTheCartChangesNothing() {
        session.setAttribute(KEY, new ArrayList<>(List.of(line(1L, null, 2))));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(7L, 1, null)));
        assertThat(cart.read(session)).extracting(CartLine::quantity).containsExactly(2);
    }

    @Test
    void severalPurchaseLinesOfOneItemAreAddedUp() {
        session.setAttribute(KEY, new ArrayList<>(List.of(line(1L, null, 5))));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(1L, 2, null), new OrderCreateItemRequest(1L, 1, null)));
        assertThat(cart.read(session)).extracting(CartLine::quantity).containsExactly(2);
    }

    @Test
    void nullArgumentsAreHarmless() {
        session.setAttribute(KEY, new ArrayList<>(List.of(line(1L, null, 5))));
        cart.removePurchased(null, List.of(new OrderCreateItemRequest(1L, 1, null)));
        cart.removePurchased(session, null);
        cart.removePurchased(session, List.of());
        cart.removePurchased(session, java.util.Collections.singletonList(null));
        cart.removePurchased(session, List.of(new OrderCreateItemRequest(1L, null, null)));
        assertThat(cart.read(session)).extracting(CartLine::quantity).containsExactly(5);
    }
}
