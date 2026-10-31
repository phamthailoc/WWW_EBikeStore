package com.ebike.productModule.controller;

import com.ebike.productModule.dto.request.ProductReviewRequest;
import com.ebike.productModule.dto.response.ProductReviewDto;
import com.ebike.productModule.service.ProductReviewService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class ProductReviewController {

    private final ProductReviewService productReviewService;

    public ProductReviewController(ProductReviewService productReviewService) {
        this.productReviewService = productReviewService;
    }

    @GetMapping("/products/{slug}/reviews")
    public List<ProductReviewDto> getProductReviews(
        @PathVariable String slug,
        Authentication authentication
    ) {
        return productReviewService.getReviews(slug, authentication);
    }

    @PostMapping("/products/{slug}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductReviewDto createProductReview(
        @PathVariable String slug,
        @RequestBody ProductReviewRequest request,
        Authentication authentication
    ) {
        return productReviewService.createReview(slug, request, authentication);
    }

    @PutMapping("/reviews/{reviewId}")
    public ProductReviewDto updateReview(
        @PathVariable Long reviewId,
        @RequestBody ProductReviewRequest request,
        Authentication authentication
    ) {
        return productReviewService.updateReview(reviewId, request, authentication);
    }

    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReview(
        @PathVariable Long reviewId,
        Authentication authentication
    ) {
        productReviewService.deleteReview(reviewId, authentication);
    }
}
