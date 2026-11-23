package com.ebike.controller.web;

import com.ebike.service.product.ProductService;
import com.ebike.service.testride.TestRideRules;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StorefrontController {
    private final ProductService products;

    /** V23: how many days ahead a test ride may be booked (the same setting the booking service enforces). */
    private final int testRideDays;

    /** The showroom's clock ({@code app.time-zone}): "today" of the booking dialog is the showroom's day, not the visitor's. */
    private final Clock clock;

    @Autowired
    public StorefrontController(ProductService products, @Value("${app.test-ride.days-ahead:14}") int testRideDays, @Value("${app.time-zone:Asia/Ho_Chi_Minh}") String timeZone) {
        this(products, testRideDays, Clock.system(ZoneId.of(timeZone)));
    }

    StorefrontController(ProductService products, int testRideDays, Clock clock) {
        this.products = products;
        this.testRideDays = Math.max(1, testRideDays);
        this.clock = clock;
    }

    @GetMapping({"/", "/shop"})
    public String home(@RequestParam(required = false) String q, Model model) {
        var catalog = products.getProducts(q, null, null, null, null, null, null, null, null, null, "featured", "desc");
        model.addAttribute("products", catalog);
        model.addAttribute("catalogProducts", catalog.stream().limit(4).toList());
        var featured = catalog.stream().filter(p -> Boolean.TRUE.equals(p.featured())).limit(4).toList();
        model.addAttribute("featured", featured.isEmpty() ? catalog.stream().limit(4).toList() : featured);
        model.addAttribute("query", q == null ? "" : q);
        // V35: a search result is not a page to list in a search engine; the plain home page is
        model.addAttribute("seoRobots", q == null || q.isBlank() ? null : "noindex,follow");
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
        // V35: the plain catalogue (and its pages) is indexed under /products; any filtered, searched or re-sorted view is not
        boolean narrowed = (q != null && !q.isBlank()) || categoryId != null || minPrice != null || maxPrice != null
            || (brand != null && !brand.isBlank()) || (vehicleType != null && !vehicleType.isBlank()) || (batteryType != null && !batteryType.isBlank())
            || minRangeKm != null || maxRangeKm != null || Boolean.TRUE.equals(inStock) || !"default".equals(sort);
        model.addAttribute("seoRobots", narrowed ? "noindex,follow" : null);
        model.addAttribute("seoPath", narrowed || safePage == 1 ? "/products" : "/products?page=" + safePage);
        model.addAttribute("filterOptions", products.getFilterOptions());
        model.addAttribute("vehicleTypeLabels", java.util.Map.of(
            "E_SCOOTER", "Xe tay ga điện", "E_MOTORBIKE", "Xe máy điện", "E_BIKE", "Xe đạp điện", "BATTERY", "Pin", "ACCESSORY", "Phụ kiện"));
        model.addAttribute("batteryTypeLabels", java.util.Map.of(
            "LEAD_ACID", "Ắc quy chì", "LITHIUM_ION", "Pin lithium-ion", "LFP", "Pin LFP"));
        model.addAttribute("categories", products.getProducts(null, null, null, null, null, null, null, null, null, null, null, null).stream().filter(p -> p.category() != null).map(p -> p.category()).distinct().toList());
        return "store/products";
    }

    @GetMapping({"/bike/{slug}", "/product/{slug}", "/models/{slug}"})
    public String detail(@PathVariable String slug, Model model) {
        var product = products.getProductBySlug(slug);
        model.addAttribute("product", product);
        model.addAttribute("related", products.getRelatedProducts(slug, 4));
        // V22: "Notify me" is for visitors (who are sent to sign in) and customers; staff accounts do not get the button.
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean visitor = authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken;
        boolean customer = !visitor && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        model.addAttribute("canNotifyStock", visitor || customer);
        // only a signed-in customer has a waiting list to ask for; guests and staff would just get 401/403
        model.addAttribute("stockAlertCustomer", customer);
        // V23: "Book a test ride" is for visitors (sent to sign in) and customers, and only for a bike that can be ridden (not a battery, an accessory or a product without a vehicle type).
        String vehicleType = product == null || product.specification() == null ? null : product.specification().vehicleType();
        boolean rideable = TestRideRules.isRideableName(vehicleType); // the same rule the booking API applies
        model.addAttribute("canTestRide", (visitor || customer) && rideable);
        model.addAttribute("testRideDays", testRideDays);
        model.addAttribute("testRideToday", LocalDate.now(clock).toString()); // the first day of the dialog's list, in the showroom's zone
        return "store/detail";
    }

    @GetMapping({"/login", "/auth"})
    public String login() { return "store/login"; }

    @GetMapping("/signup")
    public String signup() { return "store/signup"; }
}

