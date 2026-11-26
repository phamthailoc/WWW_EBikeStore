package com.ebike.qa.selenium;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/** Settings of the QA suite: -Dqa.baseUrl, -Dqa.headless and qa-selenium.properties. */
public final class QaConfig {
    private static final Properties P = new Properties();
    private static final AtomicLong SEQ = new AtomicLong();

    static {
        try (InputStream in = QaConfig.class.getResourceAsStream("/qa-selenium.properties")) {
            P.load(in);
        } catch (Exception e) {
            throw new IllegalStateException("qa-selenium.properties missing", e);
        }
    }

    private QaConfig() { }

    public static String baseUrl() {
        String url = System.getProperty("qa.baseUrl", "http://localhost:8088");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static boolean headless() { return !"false".equalsIgnoreCase(System.getProperty("qa.headless", "true")); }

    public static String get(String key) { return P.getProperty(key); }

    /** A username nobody used before: qa.sel.millis+counter. */
    public static String uniqueUsername() { return "qa.sel." + System.currentTimeMillis() + SEQ.incrementAndGet(); }
}
