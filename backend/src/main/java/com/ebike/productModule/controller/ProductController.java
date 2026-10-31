package com.ebike.productModule.controller;

import com.ebike.productModule.dto.response.ProductDetailDto;
import com.ebike.productModule.dto.response.ProductFilterOptionsDto;
import com.ebike.productModule.dto.response.ProductSummaryDto;
import com.ebike.productModule.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductSummaryDto> getProducts(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer categoryId,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) String brand,
        @RequestParam(required = false) String vehicleType,
        @RequestParam(required = false) String batteryType,
        @RequestParam(required = false) BigDecimal minRangeKm,
        @RequestParam(required = false) BigDecimal maxRangeKm,
        @RequestParam(required = false) Boolean inStock,
        @RequestParam(required = false) String sortBy,
        @RequestParam(required = false) String sortDir
    ) {
        return productService.getProducts(
            query,
            categoryId,
            minPrice,
            maxPrice,
            brand,
            vehicleType,
            batteryType,
            minRangeKm,
            maxRangeKm,
            inStock,
            sortBy,
            sortDir
        );
    }

    @GetMapping("/filter-options")
    public ProductFilterOptionsDto getFilterOptions() {
        return productService.getFilterOptions();
    }

    @GetMapping("/{slug}")
    public ProductDetailDto getProductBySlug(@PathVariable String slug) {
        return productService.getProductBySlug(slug);
    }

    @GetMapping("/{slug}/related")
    public List<ProductSummaryDto> getRelatedProducts(
        @PathVariable String slug,
        @RequestParam(defaultValue = "4") Integer limit
    ) {
        return productService.getRelatedProducts(slug, limit);
    }
}
