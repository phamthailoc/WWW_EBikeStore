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
        this.publicBaseUrl = clean(publicBaseUrl);
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
