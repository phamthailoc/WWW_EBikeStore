package com.ebike.webmodule;

import com.ebike.productModule.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StorefrontController {
    private final ProductService products;

    public StorefrontController(ProductService products) { this.products = products; }

    @GetMapping({"/", "/shop"})
    public String home(@RequestParam(required = false) String q, Model model) {
        var catalog = products.getProducts(q, null, null, null, null, null, null, null, null, null, "featured", "desc");
        model.addAttribute("products", catalog);
        model.addAttribute("catalogProducts", catalog.stream().limit(4).toList());
        var featured = catalog.stream().filter(p -> Boolean.TRUE.equals(p.featured())).limit(4).toList();
        model.addAttribute("featured", featured.isEmpty() ? catalog.stream().limit(4).toList() : featured);
        model.addAttribute("query", q == null ? "" : q);
        return "store/home";
    }

    @GetMapping({"/products", "/models"})
    public String catalog(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Integer categoryId,
        @RequestParam(required = false) java.math.BigDecimal minPrice,
        @RequestParam(required = false) java.math.BigDecimal maxPrice,
        @RequestParam(required = false) String brand,
        @RequestParam(required = false) String vehicleType,
        @RequestParam(required = false) String batteryType,
        @RequestParam(required = false) java.math.BigDecimal minRangeKm,
        @RequestParam(required = false) java.math.BigDecimal maxRangeKm,
        @RequestParam(defaultValue = "false") Boolean inStock,
        @RequestParam(defaultValue = "default") String sort,
        @RequestParam(defaultValue = "1") int page,
        Model model
    ) {
        String sortBy = null, sortDir = null;
        if ("price-asc".equals(sort)) { sortBy = "price"; sortDir = "asc"; }
        else if ("price-desc".equals(sort)) { sortBy = "price"; sortDir = "desc"; }
        else if ("name-asc".equals(sort)) { sortBy = "name"; sortDir = "asc"; }
        else if ("newest".equals(sort)) { sortBy = "newest"; sortDir = "desc"; }
        var all = products.getProducts(q, categoryId, minPrice, maxPrice, brand, vehicleType, batteryType, minRangeKm, maxRangeKm, inStock, sortBy, sortDir);
        int pageSize = 9;
        int pageCount = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        int safePage = Math.min(Math.max(page, 1), pageCount);
        model.addAttribute("products", all.subList((safePage - 1) * pageSize, Math.min(safePage * pageSize, all.size())));
        model.addAttribute("productCount", all.size());
        model.addAttribute("page", safePage);
        model.addAttribute("pageCount", pageCount);
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("categoryId", categoryId == null ? "" : categoryId);
        model.addAttribute("minPrice", minPrice == null ? "" : minPrice);
        model.addAttribute("maxPrice", maxPrice == null ? "" : maxPrice);
        model.addAttribute("vehicleType", vehicleType == null ? "" : vehicleType);
        model.addAttribute("batteryType", batteryType == null ? "" : batteryType);
        model.addAttribute("brand", brand == null ? "" : brand);
        model.addAttribute("minRangeKm", minRangeKm == null ? "" : minRangeKm);
        model.addAttribute("maxRangeKm", maxRangeKm == null ? "" : maxRangeKm);
        model.addAttribute("inStock", inStock);
        model.addAttribute("sort", sort);
        model.addAttribute("filterOptions", products.getFilterOptions());
        model.addAttribute("categories", products.getProducts(null, null, null, null, null, null, null, null, null, null, null, null).stream().filter(p -> p.category() != null).map(p -> p.category()).distinct().toList());
        return "store/products";
    }

    @GetMapping({"/bike/{slug}", "/product/{slug}", "/models/{slug}"})
    public String detail(@PathVariable String slug, Model model) {
        model.addAttribute("product", products.getProductBySlug(slug));
        model.addAttribute("related", products.getRelatedProducts(slug, 4));
        return "store/detail";
    }

    @GetMapping({"/login", "/auth"})
    public String login() { return "store/login"; }

    @GetMapping("/signup")
    public String signup() { return "store/signup"; }
}
