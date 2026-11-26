package com.ebike.qa.unit.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebike.controller.chatbot.ChatbotController;
import com.ebike.controller.product.AdminProductImageController;
import com.ebike.controller.product.ProductController;
import com.ebike.controller.product.ProductReviewController;
import com.ebike.dto.chatbot.request.ChatbotAskRequest;
import com.ebike.dto.chatbot.response.ChatbotResponse;
import com.ebike.dto.product.request.ProductReviewRequest;
import com.ebike.dto.product.response.ProductFilterOptionsDto;
import com.ebike.dto.product.response.ProductReviewDto;
import com.ebike.dto.product.response.ReviewEligibilityDto;
import com.ebike.service.chatbot.ChatbotService;
import com.ebike.service.product.AdminProductImageService;
import com.ebike.service.product.ProductReviewService;
import com.ebike.service.product.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class RestControllersTest {

    private ProductService productService;
    private ProductReviewService reviewService;
    private AdminProductImageService imageService;
    private ChatbotService chatbotService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        reviewService = mock(ProductReviewService.class);
        imageService = mock(AdminProductImageService.class);
        chatbotService = mock(ChatbotService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(productService), new ProductReviewController(reviewService),
            new AdminProductImageController(imageService), new ChatbotController(chatbotService)).build();
    }

    private static Authentication auth() {
        return new UsernamePasswordAuthenticationToken("alice", "x", AuthorityUtils.createAuthorityList("order:view-own"));
    }

    // ---- products

    @Test
    void listPassesEveryQueryParameterToTheService() throws Exception {
        when(productService.getProducts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/products?query=xe&categoryId=2&minPrice=100&maxPrice=900.5&brand=Y&vehicleType=E_BIKE&batteryType=LFP"
                + "&minRangeKm=10&maxRangeKm=90&inStock=true&sortBy=price&sortDir=desc"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(productService).getProducts("xe", 2, new BigDecimal("100"), new BigDecimal("900.5"), "Y", "E_BIKE", "LFP",
            new BigDecimal("10"), new BigDecimal("90"), true, "price", "desc");
    }

    @Test
    void malformedNumbersAreBadRequestsNotServerErrors() throws Exception {
        mvc.perform(get("/api/v1/products?minPrice=abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products?categoryId=1.5")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products?inStock=maybe")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/x/related?limit=many")).andExpect(status().isBadRequest());
    }

    @Test
    void serviceErrorsKeepTheirStatusCode() throws Exception {
        when(productService.getProductBySlug("ghost")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."));
        mvc.perform(get("/api/v1/products/ghost")).andExpect(status().isNotFound());
        when(productService.getProducts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc loại xe không hợp lệ."));
        mvc.perform(get("/api/v1/products?vehicleType=car")).andExpect(status().isBadRequest());
    }

    @Test
    void filterOptionsAreNotShadowedByTheSlugRoute() throws Exception {
        when(productService.getFilterOptions()).thenReturn(new ProductFilterOptionsDto(List.of("A"), List.of("E_BIKE"), List.of("LFP")));
        mvc.perform(get("/api/v1/products/filter-options")).andExpect(status().isOk()).andExpect(jsonPath("$.brands[0]").value("A"));
    }

    @Test
    void relatedDefaultsToFour() throws Exception {
        when(productService.getRelatedProducts("xe-1", 4)).thenReturn(List.of());
        mvc.perform(get("/api/v1/products/xe-1/related")).andExpect(status().isOk());
        verify(productService).getRelatedProducts("xe-1", 4);
        mvc.perform(get("/api/v1/products/xe-1/related?limit=7")).andExpect(status().isOk());
        verify(productService).getRelatedProducts("xe-1", 7);
    }

    // ---- reviews

    private static ProductReviewDto reviewDto() {
        return new ProductReviewDto(1L, 2L, "xe-1", 3L, "alice", "Alice", 5, "t", "c", null, true, true);
    }

    @Test
    void creatingAReviewReturns201AndPassesTheAuthentication() throws Exception {
        when(reviewService.createReview(eq("xe-1"), any(ProductReviewRequest.class), any())).thenReturn(reviewDto());
        mvc.perform(post("/api/v1/products/xe-1/reviews").principal(auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"title\":\"t\",\"comment\":\"c\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.rating").value(5));
        verify(reviewService).createReview(eq("xe-1"), eq(new ProductReviewRequest(5, "t", "c")), any(Authentication.class));
    }

    @Test
    void reviewWithoutBodyOrWithBrokenJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/products/xe-1/reviews").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/products/xe-1/reviews").contentType(MediaType.APPLICATION_JSON).content("{rating:")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/products/xe-1/reviews").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"five\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/products/xe-1/reviews").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void reviewListEligibilityUpdateAndDelete() throws Exception {
        when(reviewService.getReviews(eq("xe-1"), any())).thenReturn(List.of(reviewDto()));
        mvc.perform(get("/api/v1/products/xe-1/reviews")).andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value("alice"));
        when(reviewService.getEligibility(eq("xe-1"), any())).thenReturn(new ReviewEligibilityDto(false, "why"));
        mvc.perform(get("/api/v1/products/xe-1/reviews/eligibility")).andExpect(jsonPath("$.canReview").value(false)).andExpect(jsonPath("$.reason").value("why"));
        when(reviewService.updateReview(eq(9L), any(), any())).thenReturn(reviewDto());
        mvc.perform(put("/api/v1/reviews/9").principal(auth()).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/reviews/9").principal(auth())).andExpect(status().isNoContent());
        verify(reviewService).deleteReview(eq(9L), any());
        mvc.perform(delete("/api/v1/reviews/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void forbiddenAndConflictFromTheServiceReachTheClient() throws Exception {
        when(reviewService.createReview(any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        mvc.perform(post("/api/v1/products/xe-1/reviews").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}")).andExpect(status().isConflict());
    }

    // ---- admin images

    @Test
    void uploadIsMultipartOnlyAndReturns201() throws Exception {
        when(imageService.uploadImage(anyLong(), any(), any(), any(), any(), any())).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        mvc.perform(multipart("/api/v1/admin/product-images").file(file).param("productId", "5").param("variantId", "7")
                .param("altText", "alt").param("sortOrder", "2").param("primaryImage", "true"))
            .andExpect(status().isCreated());
        verify(imageService).uploadImage(eq(5L), eq(7L), any(), eq("alt"), eq(2), eq(true));
        mvc.perform(post("/api/v1/admin/product-images").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadWithoutProductIdOrFileIsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});
        mvc.perform(multipart("/api/v1/admin/product-images").file(file)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/admin/product-images").param("productId", "5")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/admin/product-images").file(file).param("productId", "five")).andExpect(status().isBadRequest());
    }

    @Test
    void updateFileIsOptionalAndDeleteIsNoContent() throws Exception {
        mvc.perform(multipart("/api/v1/admin/product-images/3").with(r -> {
            r.setMethod("PUT");
            return r;
        }).param("altText", "x")).andExpect(status().isOk());
        verify(imageService).updateImage(eq(3L), isNull(), eq("x"), isNull(), isNull());
        mvc.perform(delete("/api/v1/admin/product-images/3")).andExpect(status().isNoContent());
        verify(imageService).deleteImage(3L);
        mvc.perform(get("/api/v1/admin/product-images/by-product/5")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/product-images/3")).andExpect(status().isOk());
    }

    @Test
    void s3FailureOnDeleteIsReportedAsBadGateway() throws Exception {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY)).when(imageService).deleteImage(3L);
        mvc.perform(delete("/api/v1/admin/product-images/3")).andExpect(status().isBadGateway());
    }

    // ---- chatbot

    @Test
    void chatbotAskReturnsTheServiceAnswerAndBlankMessageIs400() throws Exception {
        when(chatbotService.ask(any(ChatbotAskRequest.class))).thenReturn(new ChatbotResponse("hi", "faq", List.of()));
        mvc.perform(post("/api/v1/chatbot/ask").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"xin chào\",\"chatId\":\"c1\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("hi")).andExpect(jsonPath("$.matchedIntent").value("faq"));
        verify(chatbotService).ask(new ChatbotAskRequest("xin chào", null, "c1"));
        when(chatbotService.ask(any(ChatbotAskRequest.class))).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập câu hỏi."));
        mvc.perform(post("/api/v1/chatbot/ask").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/chatbot/ask")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/chatbot/ask").contentType(MediaType.APPLICATION_JSON).content("null")).andExpect(status().isBadRequest());
    }

    @Test
    void chatbotAcceptsTheLegacyContentField() throws Exception {
        when(chatbotService.ask(any(ChatbotAskRequest.class))).thenReturn(new ChatbotResponse("hi", "faq", List.of()));
        mvc.perform(post("/api/v1/chatbot/ask").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hello\"}")).andExpect(status().isOk());
        verify(chatbotService).ask(new ChatbotAskRequest(null, "hello", null));
    }
}
