package com.ebike.service.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** V27: who the shop is on a sales receipt and a warranty card. The values come from {@code app.store.*}; the tax code is a made-up number. */
@Component
public class StoreProfile {

    private final String name;
    private final String legalName;
    private final String taxCode;
    private final String address;
    private final String phone;
    private final String email;
    private final String publicBaseUrl;

    public StoreProfile(
        @Value("${app.store.name:Kinetic E-Bike}") String name,
        @Value("${app.store.legal-name:Kinetic E-Bike}") String legalName,
        @Value("${app.store.tax-code:}") String taxCode,
        @Value("${app.store.address:}") String address,
        @Value("${app.store.phone:}") String phone,
        @Value("${app.store.email:}") String email,
        @Value("${app.public-base-url:}") String publicBaseUrl
    ) {
        this.name = clean(name);
        this.legalName = clean(legalName);
        this.taxCode = clean(taxCode);
        this.address = clean(address);
        this.phone = clean(phone);
        this.email = clean(email);
        // a deployment that forgot APP_PUBLIC_BASE_URL keeps the localhost default: print no address rather than a wrong one
        this.publicBaseUrl = isLocalAddress(publicBaseUrl) ? "" : clean(publicBaseUrl);
    }

    /** True for an address on the machine itself (the default {@code http://localhost:8080}), which means nothing to a customer reading a PDF or an email. */
    public static boolean isLocalAddress(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String host = java.net.URI.create(url.trim()).getHost();
            return host != null && (host.equalsIgnoreCase("localhost") || host.endsWith(".localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName.isEmpty() ? name : legalName;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    /** The warranty lookup page, or empty when the shop has no public address configured. */
    public String warrantyLookupUrl() {
        if (publicBaseUrl.isEmpty()) {
            return "";
        }
        return (publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl) + "/warranty";
    }
}
