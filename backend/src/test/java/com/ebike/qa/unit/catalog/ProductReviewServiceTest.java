package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.product.request.ProductReviewRequest;
import com.ebike.dto.product.response.ProductReviewDto;
import com.ebike.dto.product.response.ReviewEligibilityDto;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.Review;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.product.ReviewRepository;
import com.ebike.service.product.ProductReviewService;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.ResponseStatusException;

class ProductReviewServiceTest {

    private ReviewRepository reviews;
    private ProductRepository products;
    private UserRepository users;
    private OrderRepository orders;
    private ProductReviewService service;
    private Product product;
    private User alice;
    private final List<Review> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reviews = mock(ReviewRepository.class);
        products = mock(ProductRepository.class);
        users = mock(UserRepository.class);
        orders = mock(OrderRepository.class);
        service = new ProductReviewService(reviews, products, users, orders);
        product = Fixtures.product(10, "Bike", "100");
        alice = Fixtures.user(1, "alice");
        when(products.findBySlug("bike")).thenReturn(Optional.of(product));
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(reviews.save(any(Review.class))).thenAnswer(i -> {
            Review r = i.getArgument(0);
            if (!stored.contains(r)) {
                stored.add(r);
            }
            return r;
        });
        when(reviews.findByProductId(10L)).thenAnswer(i -> new ArrayList<>(stored));
    }

    private Authentication auth(String name, String... authorities) {
        return new UsernamePasswordAuthenticationToken(name, "x", AuthorityUtils.createAuthorityList(authorities));
    }

    private static ProductReviewRequest req(Integer rating, String title, String comment) {
        return new ProductReviewRequest(rating, title, comment);
    }

    private static void status(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private Review review(long id, User user, int rating) {
        Review review = new Review();
        review.setId(id);
        review.setProduct(product);
        review.setUser(user);
        review.setRating(rating);
        stored.add(review);
        return review;
    }

    // ---- create

    @Test
    void customerWhoReceivedTheBikeCanReview() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        ProductReviewDto dto = service.createReview("bike", req(5, "  Great ", "  fine  "), auth("alice"));
        assertThat(dto.rating()).isEqualTo(5);
        assertThat(dto.title()).isEqualTo("Great");
        assertThat(dto.comment()).isEqualTo("fine");
        assertThat(dto.verifiedPurchase()).isTrue();
        assertThat(dto.editableByCurrentUser()).isTrue();
        assertThat(product.getReviewCount()).isEqualTo(1);
        assertThat(product.getRating()).isEqualByComparingTo("5.00");
        verify(products).save(product);
    }

    @Test
    void blankTitleAndCommentAreStoredAsNull() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        ProductReviewDto dto = service.createReview("bike", req(3, "   ", ""), auth("alice"));
        assertThat(dto.title()).isNull();
        assertThat(dto.comment()).isNull();
    }

    @Test
    void customerWhoNeverReceivedTheBikeIsForbidden() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(false);
        status(() -> service.createReview("bike", req(5, null, null), auth("alice")), HttpStatus.FORBIDDEN);
        verify(reviews, never()).save(any());
    }

    @Test
    void secondReviewOfTheSameProductIsAConflictEvenWhenBought() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        when(reviews.findByProductSlugAndUserUsername("bike", "alice")).thenReturn(Optional.of(review(1, alice, 4)));
        status(() -> service.createReview("bike", req(5, null, null), auth("alice")), HttpStatus.CONFLICT);
        verify(reviews, never()).save(any());
    }

    @Test
    void unauthenticatedBlankOrUnknownUsersGet401() {
        status(() -> service.createReview("bike", req(5, null, null), null), HttpStatus.UNAUTHORIZED);
        status(() -> service.createReview("bike", req(5, null, null), auth("  ")), HttpStatus.UNAUTHORIZED);
        when(users.findByEmail("ghost")).thenReturn(Optional.empty());
        status(() -> service.createReview("bike", req(5, null, null), auth("ghost")), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userIsAlsoFoundByEmail() {
        when(users.findByUsername("alice@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        assertThat(service.createReview("bike", req(4, null, null), auth("alice@example.com")).username()).isEqualTo("alice");
    }

    @Test
    void inactiveOrUnknownProductIs404AndBlankSlugIs400() {
        product.setActive(false);
        status(() -> service.createReview("bike", req(5, null, null), auth("alice")), HttpStatus.NOT_FOUND);
        status(() -> service.createReview("nope", req(5, null, null), auth("alice")), HttpStatus.NOT_FOUND);
        status(() -> service.createReview("  ", req(5, null, null), auth("alice")), HttpStatus.BAD_REQUEST);
        status(() -> service.createReview(null, req(5, null, null), auth("alice")), HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6, 100, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void ratingOutsideOneToFiveIsRejected(int rating) {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        status(() -> service.createReview("bike", req(rating, null, null), auth("alice")), HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void ratingBoundariesAreAccepted(int rating) {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        assertThat(service.createReview("bike", req(rating, null, null), auth("alice")).rating()).isEqualTo(rating);
    }

    @Test
    void nullRatingAndNullRequestAreBadRequests() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        status(() -> service.createReview("bike", req(null, "t", "c"), auth("alice")), HttpStatus.BAD_REQUEST);
        status(() -> service.createReview("bike", null, auth("alice")), HttpStatus.BAD_REQUEST);
    }

    @Test
    void titleAndCommentLengthLimitsAreExactlyTrimmed255And2000() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        status(() -> service.createReview("bike", req(5, "x".repeat(256), null), auth("alice")), HttpStatus.BAD_REQUEST);
        status(() -> service.createReview("bike", req(5, null, "x".repeat(2001)), auth("alice")), HttpStatus.BAD_REQUEST);
        assertThat(service.createReview("bike", req(5, "x".repeat(255), "y".repeat(2000)), auth("alice")).title()).hasSize(255);
    }

    @Test
    void paddingDoesNotCountTowardTheLimit() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        assertThat(service.createReview("bike", req(5, " ".repeat(400) + "ok", null), auth("alice")).title()).isEqualTo("ok");
    }

    @Test
    void unicodeCommentIsKeptIntact() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        String text = "Xe rất tốt 😀 đi êm";
        assertThat(service.createReview("bike", req(5, null, text), auth("alice")).comment()).isEqualTo(text);
    }

    // ---- rating recount

    private void addReviews(int... ratings) {
        long id = 100;
        for (int r : ratings) {
            review(id++, Fixtures.user(id, "u" + id), r);
        }
    }

    @Test
    void averageIsRoundedHalfUpToTwoDecimals() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        addReviews(5, 5);
        service.createReview("bike", req(4, null, null), auth("alice"));
        assertThat(product.getRating()).isEqualByComparingTo("4.67");
        assertThat(product.getReviewCount()).isEqualTo(3);
    }

    @Test
    void exactHalfCentimalRoundsUp() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        addReviews(4, 4, 4, 4, 4, 4, 4); // + 5 below -> 33/8 = 4.125
        service.createReview("bike", req(5, null, null), auth("alice"));
        assertThat(product.getRating()).isEqualByComparingTo("4.13");
    }

    @Test
    void averageOfFortyReviewsThatIsFourPointSixSevenFive() {
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        int[] ratings = new int[39];
        int sum = 0;
        for (int i = 0; i < 39; i++) {
            ratings[i] = i < 27 ? 5 : 4; // 27*5 + 12*4 = 183
            sum += ratings[i];
        }
        addReviews(ratings);
        service.createReview("bike", req(4, null, null), auth("alice")); // 187 / 40
        assertThat(sum + 4).isEqualTo(187);
        assertThat(product.getRating()).isEqualByComparingTo("4.68");
    }

    @Test
    void deletingTheLastReviewResetsRatingToZero() {
        Review mine = review(7, alice, 5);
        product.setRating(new java.math.BigDecimal("5.00"));
        product.setReviewCount(1);
        when(reviews.findById(7L)).thenReturn(Optional.of(mine));
        org.mockito.Mockito.doAnswer(i -> stored.remove((Review) i.getArgument(0))).when(reviews).delete(any(Review.class));
        service.deleteReview(7L, auth("alice"));
        assertThat(product.getReviewCount()).isZero();
        assertThat(product.getRating()).isEqualByComparingTo("0");
        verify(reviews).flush();
    }

    // ---- edit / delete ownership

    @Test
    void ownerCanEditAndRatingIsRecounted() {
        Review mine = review(7, alice, 1);
        when(reviews.findById(7L)).thenReturn(Optional.of(mine));
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        ProductReviewDto dto = service.updateReview(7L, req(5, "new", "text"), auth("alice"));
        assertThat(dto.rating()).isEqualTo(5);
        assertThat(product.getRating()).isEqualByComparingTo("5.00");
        assertThat(dto.verifiedPurchase()).isTrue();
    }

    @Test
    void anotherCustomerCannotEditOrDelete() {
        User bob = Fixtures.user(2, "bob");
        when(users.findByUsername("bob")).thenReturn(Optional.of(bob));
        Review mine = review(7, alice, 1);
        when(reviews.findById(7L)).thenReturn(Optional.of(mine));
        status(() -> service.updateReview(7L, req(5, null, null), auth("bob")), HttpStatus.FORBIDDEN);
        status(() -> service.deleteReview(7L, auth("bob")), HttpStatus.FORBIDDEN);
        assertThat(mine.getRating()).isEqualTo(1);
        verify(reviews, never()).delete(any());
    }

    @Test
    void forbiddenComesBeforeValidationSoAStrangerLearnsNothing() {
        User bob = Fixtures.user(2, "bob");
        when(users.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(reviews.findById(7L)).thenReturn(Optional.of(review(7, alice, 1)));
        status(() -> service.updateReview(7L, null, auth("bob")), HttpStatus.FORBIDDEN);
    }

    @Test
    void moderatorMayEditAndDeleteAnyReview() {
        User mod = Fixtures.user(3, "mod");
        when(users.findByUsername("mod")).thenReturn(Optional.of(mod));
        Review mine = review(7, alice, 2);
        when(reviews.findById(7L)).thenReturn(Optional.of(mine));
        service.updateReview(7L, req(1, "spam", null), auth("mod", "review:moderate"));
        assertThat(mine.getTitle()).isEqualTo("spam");
        service.deleteReview(7L, auth("mod", "review:moderate"));
        verify(reviews).delete(mine);
    }

    @Test
    void lookalikeAuthoritiesDoNotModerate() {
        User mod = Fixtures.user(3, "mod");
        when(users.findByUsername("mod")).thenReturn(Optional.of(mod));
        when(reviews.findById(7L)).thenReturn(Optional.of(review(7, alice, 2)));
        status(() -> service.deleteReview(7L, auth("mod", "ROLE_ADMIN", "review:view", "REVIEW:MODERATE")), HttpStatus.FORBIDDEN);
    }

    @Test
    void missingReviewIs404AndAnonymousIs401() {
        when(reviews.findById(99L)).thenReturn(Optional.empty());
        status(() -> service.updateReview(99L, req(5, null, null), auth("alice")), HttpStatus.NOT_FOUND);
        status(() -> service.deleteReview(99L, auth("alice")), HttpStatus.NOT_FOUND);
        status(() -> service.deleteReview(99L, null), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidUpdateByTheOwnerIsRejectedAndNothingChanges() {
        Review mine = review(7, alice, 3);
        when(reviews.findById(7L)).thenReturn(Optional.of(mine));
        status(() -> service.updateReview(7L, req(9, "x", "y"), auth("alice")), HttpStatus.BAD_REQUEST);
        assertThat(mine.getRating()).isEqualTo(3);
        assertThat(mine.getTitle()).isNull();
    }

    // ---- listing / eligibility

    @Test
    void listingMarksVerifiedBuyersAndEditableReviews() {
        User bob = Fixtures.user(2, "bob");
        Review a = review(1, alice, 5);
        Review b = review(2, bob, 4);
        when(orders.findDeliveredBuyerIds(10L)).thenReturn(List.of(1L));
        when(reviews.findByProductSlugOrderByCreatedAtDesc("bike")).thenReturn(List.of(a, b));
        List<ProductReviewDto> list = service.getReviews("bike", auth("alice"));
        assertThat(list).extracting(ProductReviewDto::verifiedPurchase).containsExactly(true, false);
        assertThat(list).extracting(ProductReviewDto::editableByCurrentUser).containsExactly(true, false);
        assertThat(service.getReviews("bike", null)).extracting(ProductReviewDto::editableByCurrentUser).containsOnly(false);
        assertThat(service.getReviews("bike", auth("x", "review:moderate"))).extracting(ProductReviewDto::editableByCurrentUser).containsOnly(true);
    }

    @Test
    void authorNameFallsBackToUsernameWhenNameIsBlank() {
        alice.setFirstName(" ");
        alice.setLastName(null);
        Review a = review(1, alice, 5);
        when(reviews.findByProductSlugOrderByCreatedAtDesc("bike")).thenReturn(List.of(a));
        assertThat(service.getReviews("bike", null).get(0).authorName()).isEqualTo("alice");
        alice.setFirstName("An");
        alice.setLastName("Nguyen");
        assertThat(service.getReviews("bike", null).get(0).authorName()).isEqualTo("An Nguyen");
    }

    @Test
    void listingAnInactiveProductIs404() {
        product.setActive(false);
        status(() -> service.getReviews("bike", null), HttpStatus.NOT_FOUND);
    }

    @Test
    void eligibilityForAnonymousVisitors() {
        AnonymousAuthenticationToken anon = new AnonymousAuthenticationToken("k", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertThat(service.getEligibility("bike", anon).canReview()).isFalse();
        assertThat(service.getEligibility("bike", null).reason()).contains("Đăng nhập");
        assertThat(service.getEligibility("bike", auth(" ")).canReview()).isFalse();
    }

    @Test
    void eligibilityStates() {
        assertThat(service.getEligibility("bike", auth("alice"))).isEqualTo(new ReviewEligibilityDto(false, "Chỉ khách đã nhận xe mới có thể đánh giá sản phẩm này."));
        when(orders.hasDeliveredProduct(1L, 10L)).thenReturn(true);
        assertThat(service.getEligibility("bike", auth("alice"))).isEqualTo(new ReviewEligibilityDto(true, null));
        when(reviews.findByProductSlugAndUserUsername("bike", "alice")).thenReturn(Optional.of(review(1, alice, 5)));
        assertThat(service.getEligibility("bike", auth("alice")).canReview()).isFalse();
    }

    // ---- account erasure

    @Test
    void erasingAnAccountRemovesItsReviewsAndRecountsEachProductOnce() {
        Review r1 = review(1, alice, 5);
        Product other = Fixtures.product(11, "Other", "1");
        Review r2 = new Review();
        r2.setId(2L);
        r2.setProduct(other);
        r2.setUser(alice);
        r2.setRating(1);
        Review r3 = new Review();
        r3.setId(3L);
        r3.setProduct(product);
        r3.setUser(alice);
        r3.setRating(2);
        when(reviews.findByUserId(1L)).thenReturn(List.of(r1, r2, r3));
        when(reviews.findByProductId(11L)).thenReturn(List.of());
        service.removeReviewsOf(1L);
        verify(reviews).deleteAll(List.of(r1, r2, r3));
        verify(products, times(2)).save(any(Product.class));
        assertThat(other.getReviewCount()).isZero();
    }

    // ---- cache contract

    @Test
    void everyMutationEvictsBothProductCaches() throws Exception {
        for (Method m : new Method[] {
            ProductReviewService.class.getMethod("createReview", String.class, ProductReviewRequest.class, Authentication.class),
            ProductReviewService.class.getMethod("updateReview", Long.class, ProductReviewRequest.class, Authentication.class),
            ProductReviewService.class.getMethod("deleteReview", Long.class, Authentication.class),
            ProductReviewService.class.getMethod("removeReviewsOf", Long.class)}) {
            Caching caching = m.getAnnotation(Caching.class);
            assertThat(caching).as(m.getName()).isNotNull();
            assertThat(caching.evict()).extracting(CacheEvict::allEntries).containsOnly(true);
            assertThat(caching.evict()).hasSize(2);
        }
    }
}
