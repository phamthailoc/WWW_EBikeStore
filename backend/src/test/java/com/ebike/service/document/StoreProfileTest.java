package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** V27: a deployment that kept the localhost default of the public address prints no lookup address, instead of a wrong one. */
class StoreProfileTest {

    private static StoreProfile storeAt(String publicBaseUrl) {
        return new StoreProfile("Kinetic E-Bike", "", "", "", "", "", publicBaseUrl);
    }

    @Test
    void aPublicAddressGivesTheWarrantyLookupPage() {
        assertEquals("https://shop.example.com/warranty", storeAt("https://shop.example.com/").warrantyLookupUrl());
    }

    @Test
    void aLocalAddressGivesNothing() {
        assertEquals("", storeAt("http://localhost:8080").warrantyLookupUrl());
        assertEquals("", storeAt("http://127.0.0.1:8080").warrantyLookupUrl());
        assertEquals("", storeAt("").warrantyLookupUrl());
    }

    @Test
    void onlyAddressesOnTheMachineItselfAreLocal() {
        assertTrue(StoreProfile.isLocalAddress("http://LocalHost:8080/"));
        assertFalse(StoreProfile.isLocalAddress("https://localhost.example.com"));
        assertFalse(StoreProfile.isLocalAddress("not a url"));
        assertFalse(StoreProfile.isLocalAddress(null));
    }
}
