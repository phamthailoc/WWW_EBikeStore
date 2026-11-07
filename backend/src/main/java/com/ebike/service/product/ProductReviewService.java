package com.ebike.service.product;

import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.config.RedisCacheConfiguration;
import com.ebike.dto.product.request.ProductReviewRequest;
import com.ebike.dto.product.response.ProductReviewDto;
import com.ebike.dto.product.response.ReviewEligibilityDto;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.Review;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.product.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_COMMENT_LENGTH = 2000;
    private static final String REVIEW_MODERATE_PERMISSION = "review:moderate";

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public ProductReviewService(
        ReviewRepository reviewRepository,
        ProductRepository productRepository,
        UserRepository userRepository,
        OrderRepository orderRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductReviewDto> getReviews(String productSlug, Authentication authentication) {
        String currentUsername = authentication == null ? null : authentication.getName();
        boolean canModerate = hasAuthority(authentication, REVIEW_MODERATE_PERMISSION);
        Set<Long> buyers = new HashSet<>(orderRepository.findDeliveredBuyerIds(findActiveProduct(productSlug).getId()));
        return reviewRepository.findByProductSlugOrderByCreatedAtDesc(productSlug).stream()
            .map(review -> toDto(review, canEdit(review, currentUsername, canModerate), review.getUser() != null && buyers.contains(review.getUser().getId())))
            .toList();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public ProductReviewDto createReview(String productSlug, ProductReviewRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        Product product = findActiveProduct(productSlug);
        validateRequest(request);

        reviewRepository.findByProductSlugAndUserUsername(productSlug, user.getUsername())
            .ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã đánh giá sản phẩm này rồi. Hãy sửa đánh giá cũ.");
            });
        if (!orderRepository.hasDeliveredProduct(user.getId(), product.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ khách đã nhận xe mới có thể đánh giá sản phẩm này.");
        }

        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        applyRequest(review, request);

        Review savedReview = reviewRepository.save(review);
        refreshProductRating(product);
        return toDto(savedReview, true, true);
    }

    /** Tells the product page whether to show the review form or the reason there is none. */
    @Transactional(readOnly = true)
    public ReviewEligibilityDto getEligibility(String productSlug, Authentication authentication) {
        Product product = findActiveProduct(productSlug);
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken || isBlank(authentication.getName())) {
            return new ReviewEligibilityDto(false, "Đăng nhập để đánh giá sản phẩm.");
        }
        User user = currentUser(authentication);
        if (reviewRepository.findByProductSlugAndUserUsername(productSlug, user.getUsername()).isPresent()) {
            return new ReviewEligibilityDto(false, "Bạn đã đánh giá sản phẩm này. Bạn có thể sửa hoặc xóa đánh giá của mình.");
        }
        if (!orderRepository.hasDeliveredProduct(user.getId(), product.getId())) {
            return new ReviewEligibilityDto(false, "Chỉ khách đã nhận xe mới có thể đánh giá sản phẩm này.");
        }
        return new ReviewEligibilityDto(true, null);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public ProductReviewDto updateReview(Long reviewId, ProductReviewRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        Review review = findReview(reviewId);
        boolean canModerate = hasAuthority(authentication, REVIEW_MODERATE_PERMISSION);
        if (!isOwner(review, user) && !canModerate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn chỉ sửa được đánh giá của chính mình.");
        }

        validateRequest(request);
        applyRequest(review, request);

        Review savedReview = reviewRepository.save(review);
        refreshProductRating(savedReview.getProduct());
        boolean bought = review.getUser() != null && orderRepository.hasDeliveredProduct(review.getUser().getId(), savedReview.getProduct().getId());
        return toDto(savedReview, true, bought);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_LIST_CACHE, allEntries = true),
        @CacheEvict(cacheNames = RedisCacheConfiguration.PRODUCT_DETAIL_CACHE, allEntries = true)
    })
    public void deleteReview(Long reviewId, Authentication authentication) {
        User user = currentUser(authentication);
        Review review = findReview(reviewId);
        boolean canModerate = hasAuthority(authentication, REVIEW_MODERATE_PERMISSION);
        if (!isOwner(review, user) && !canModerate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn chỉ xóa được đánh giá của chính mình.");
        }

        Product product = review.getProduct();
        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshProductRating(product);
    }

    private Product findActiveProduct(String productSlug) {
        if (isBlank(productSlug)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product slug is required");
        }
        return productRepository.findBySlug(productSlug)
            .filter(product -> Boolean.TRUE.equals(product.getActive()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private Review findReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || isBlank(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
            .or(() -> userRepository.findByEmail(authentication.getName()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private void validateRequest(ProductReviewRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.rating() == null || request.rating() < MIN_RATING || request.rating() > MAX_RATING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn số sao từ 1 đến 5.");
        }
        if (normalize(request.title()) != null && normalize(request.title()).length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tiêu đề đánh giá quá dài.");
        }
        if (normalize(request.comment()) != null && normalize(request.comment()).length() > MAX_COMMENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung đánh giá quá dài (tối đa 2000 ký tự).");
        }
    }

    private void applyRequest(Review review, ProductReviewRequest request) {
        review.setRating(request.rating());
        review.setTitle(normalize(request.title()));
        review.setComment(normalize(request.comment()));
    }

    private void refreshProductRating(Product product) {
        List<Review> reviews = reviewRepository.findByProductId(product.getId());
        product.setReviewCount(reviews.size());
        if (reviews.isEmpty()) {
            product.setRating(BigDecimal.ZERO);
        } else {
            double average = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0);
            product.setRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        }
        productRepository.save(product);
    }

    private ProductReviewDto toDto(Review review, boolean editableByCurrentUser, boolean verifiedPurchase) {
        User user = review.getUser();
        Product product = review.getProduct();
        return new ProductReviewDto(
            review.getId(),
            product.getId(),
            product.getSlug(),
            user.getId(),
            user.getUsername(),
            buildAuthorName(user),
            review.getRating(),
            review.getTitle(),
            review.getComment(),
            review.getCreatedAt(),
            editableByCurrentUser,
            verifiedPurchase
        );
    }

    private boolean canEdit(Review review, String currentUsername, boolean canModerate) {
        return canModerate || (currentUsername != null && review.getUser() != null && currentUsername.equals(review.getUser().getUsername()));
    }

    private boolean isOwner(Review review, User user) {
        return review.getUser() != null && user != null && review.getUser().getId().equals(user.getId());
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(authority::equals);
    }

    private String buildAuthorName(User user) {
        String firstName = normalize(user.getFirstName());
        String lastName = normalize(user.getLastName());
        String fullName = String.join(" ", List.of(
            firstName == null ? "" : firstName,
            lastName == null ? "" : lastName
        )).trim();
        return fullName == null || fullName.isBlank() ? user.getUsername() : fullName;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
