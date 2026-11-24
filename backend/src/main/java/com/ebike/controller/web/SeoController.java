package com.ebike.controller.web;

import com.ebike.service.product.ProductService;
import com.ebike.service.seo.SeoAddress;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V35: {@code /sitemap.xml} (every product on sale, read from the product list cache that stock, price and review changes already
 * evict) and {@code /robots.txt}. Both are public and are rebuilt on each request, so a product that is switched off or renamed
 * drops out within ten minutes: crawlers and proxies may keep the answer that long.
 */
@RestController
public class SeoController {
    private static final CacheControl CACHE = CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic();

    private final ProductService products;
    private final SeoAddress address;

    public SeoController(ProductService products, SeoAddress address) {
        this.products = products;
        this.address = address;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        var catalog = products.getProducts(null, null, null, null, null, null, null, null, null, null, null, null);
        return ResponseEntity.ok().cacheControl(CACHE).headers(address.varyHeaders()).body(address.markup(request).sitemapXml(catalog));
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> robots(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CACHE).headers(address.varyHeaders()).body(address.markup(request).robotsTxt());
    }
}
