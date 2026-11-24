package com.ebike.service.seo;

import com.ebike.service.document.StoreProfile;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * V35: the one place that decides which address the shop's pages, sitemap and robots.txt use: the configured public address
 * ({@code app.public-base-url}), or, when none is set (or only the local default is), the address each visitor came by.
 */
@Component
public class SeoAddress {
    private static final Logger LOG = LoggerFactory.getLogger(SeoAddress.class);

    private final String configured;

    public SeoAddress(@Value("${app.public-base-url:}") String configured) {
        this.configured = configured;
    }

    @PostConstruct
    void warnWhenAddressIsNotSet() {
        if (!isConfigured()) {
            LOG.warn("APP_PUBLIC_BASE_URL is not set to a public address: sitemap.xml, robots.txt and the page canonical links use the address each visitor came by. Set it when deploying.");
        }
    }

    /** True when an address that means something to a crawler is set: blank and the local default (localhost, 127.0.0.1) count as not set. */
    public boolean isConfigured() {
        return configured != null && !configured.isBlank() && !StoreProfile.isLocalAddress(configured);
    }

    /** The sitemap and robots.txt are shared-cache friendly only when they do not depend on the Host header. */
    public HttpHeaders varyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (!isConfigured()) {
            headers.add(HttpHeaders.VARY, "Host");
        }
        return headers;
    }

    /** The markup helper for this request. */
    public SeoMarkup markup(HttpServletRequest request) {
        if (isConfigured()) {
            return new SeoMarkup(configured);
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean standard = port <= 0 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        return new SeoMarkup(scheme + "://" + request.getServerName() + (standard ? "" : ":" + port) + context);
    }
}
