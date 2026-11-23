package com.ebike.controller.web;

import com.ebike.service.product.ProductService;
import com.ebike.service.seo.SeoMarkup;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V35: {@code /sitemap.xml} (every product on sale, read from the product list cache that stock, price and review changes already
 * evict) and {@code /robots.txt}. Both are public and are rebuilt on each request, so a product that is switched off or renamed
 * drops out at once; crawlers and proxies may keep the answer for ten minutes.
 */
@RestController
public class SeoController {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SeoController.class);
    private static final CacheControl CACHE = CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic();

    private final ProductService products;
    private final String publicBaseUrl;

    @Autowired
    public SeoController(ProductService products, @Value("${app.public-base-url:}") String publicBaseUrl) {
        this.products = products;
        this.publicBaseUrl = publicBaseUrl;
    }

    @jakarta.annotation.PostConstruct
    void warnWhenAddressIsNotSet() {
        if (!isConfigured(publicBaseUrl)) {
            LOG.warn("APP_PUBLIC_BASE_URL is not set to a public address: sitemap.xml, robots.txt and the page canonical links use the address each visitor came by. Set it when deploying.");
        }
    }

    /** The sitemap and robots.txt are shared-cache friendly only when they do not depend on the Host header. */
    private org.springframework.http.HttpHeaders varyHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (!isConfigured(publicBaseUrl)) {
            headers.add(org.springframework.http.HttpHeaders.VARY, "Host");
        }
        return headers;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        var catalog = products.getProducts(null, null, null, null, null, null, null, null, null, null, null, null);
        return ResponseEntity.ok().cacheControl(CACHE).headers(varyHeaders()).body(markup(publicBaseUrl, request).sitemapXml(catalog));
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> robots(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CACHE).headers(varyHeaders()).body(markup(publicBaseUrl, request).robotsTxt());
    }

    /** True when an address that means something to a crawler is set: blank and the local default (localhost, 127.0.0.1) count as not set. */
    static boolean isConfigured(String configured) {
        return configured != null && !configured.isBlank() && !com.ebike.service.document.StoreProfile.isLocalAddress(configured);
    }

    /** The shop's configured public address, or the address this request came by when none is configured (or only the local default is). */
    static SeoMarkup markup(String configured, HttpServletRequest request) {
        if (isConfigured(configured)) {
            return new SeoMarkup(configured);
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean standard = port <= 0 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        return new SeoMarkup(scheme + "://" + request.getServerName() + (standard ? "" : ":" + port) + context);
    }

}
