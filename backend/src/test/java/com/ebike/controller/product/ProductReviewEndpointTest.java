package com.ebike.controller.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.service.product.ProductReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The product page calls the review API under /api/v1, like every other API and the security rules. */
class ProductReviewEndpointTest {

    private final ProductReviewService service = mock(ProductReviewService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductReviewController(service)).build();

    @Test
    void reviewsAndEligibilityLiveUnderTheApiPrefix() throws Exception {
        mvc.perform(get("/api/v1/products/kinetic-sir-50cc/reviews")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/kinetic-sir-50cc/reviews/eligibility")).andExpect(status().isOk());
        verify(service).getReviews(eq("kinetic-sir-50cc"), any());
        verify(service).getEligibility(eq("kinetic-sir-50cc"), any());
    }
}
