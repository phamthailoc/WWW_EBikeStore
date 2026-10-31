package com.ebike.productModule.service;

import com.ebike.authModule.entity.User;
import com.ebike.authModule.repository.UserRepository;
import com.ebike.config.RedisCacheConfiguration;
import com.ebike.productModule.dto.request.ProductReviewRequest;
import com.ebike.productModule.dto.response.ProductReviewDto;
import com.ebike.productModule.entity.Product;
import com.ebike.productModule.entity.Review;
import com.ebike.productModule.repository.ProductRepository;
import com.ebike.productModule.repository.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
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

    public ProductReviewService(
        ReviewRepository reviewRepository,
        ProductRepository productRepository,
        UserRepository userRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductReviewDto> getReviews(String productSlug, Authentication authentication) {
        String currentUsername = authentication == null ? null : authentication.getName();
        boolean canModerate = hasAuthority(authentication, REVIEW_MODERATE_PERMISSION);
        return reviewRepository.findByProductSlugOrderByCreatedAtDesc(productSlug).stream()
            .map(review -> toDto(review, canEdit(review, currentUsername, canModerate)))
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
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product");
            });

        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        applyRequest(review, request);

        Review savedReview = reviewRepository.save(review);
        refreshProductRating(product);
        return toDto(savedReview, true);
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own review");
        }

        validateRequest(request);
        applyRequest(review, request);

        Review savedReview = reviewRepository.save(review);
        refreshProductRating(savedReview.getProduct());
        return toDto(savedReview, true);
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own review");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
        }
        if (normalize(request.title()) != null && normalize(request.title()).length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review title is too long");
        }
        if (normalize(request.comment()) != null && normalize(request.comment()).length() > MAX_COMMENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review comment is too long");
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

    private ProductReviewDto toDto(Review review, boolean editableByCurrentUser) {
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
            editableByCurrentUser
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
