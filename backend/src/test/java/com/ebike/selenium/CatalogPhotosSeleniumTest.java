package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * The verified electric catalog (update_verified_electric_catalog.sql): every bike for sale is listed, the old
 * petrol 50cc models are gone, and every product photo the browser shows actually loads (no 404 or broken image).
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class CatalogPhotosSeleniumTest extends StorefrontSeleniumTest {

    /** Petrol models switched off by the catalog migration. */
    private static final List<String> RETIRED = List.of("kinetic-sir-50cc", "kinetic-rs-50cc");

    /** Loads lazy images too, waits until each has finished, then returns the ones that failed. */
    @SuppressWarnings("unchecked")
    private List<String> brokenImages() {
        js("document.querySelectorAll('img[loading=lazy]').forEach(i=>i.loading='eager')");
        wait.until(d -> Boolean.TRUE.equals(js("return [...document.images].every(i=>i.complete)")));
        return (List<String>) js("return [...document.images].filter(i=>i.getAttribute('src')&&i.naturalWidth===0)"
            + ".map(i=>i.getAttribute('src'))");
    }

    @Test
    void everyBikeForSaleIsListedWithPhotosThatLoad() throws Exception {
        Set<String> forSale = new TreeSet<>();
        for (JsonNode product : listOf(call(api, "GET", "/api/v1/products", null))) {
            forSale.add(product.path("slug").asText());
        }
        assertTrue(forSale.size() >= 30, "the catalog lists the electric bikes: " + forSale.size());
        RETIRED.forEach(slug -> assertTrue(!forSale.contains(slug), slug + " is no longer for sale"));

        Set<String> listed = new TreeSet<>();
        List<String> broken = new ArrayList<>();
        for (int page = 1; page <= 10; page++) {
            driver.get(baseUrl + "/products?page=" + page);
            List<WebElement> links = driver.findElements(By.cssSelector("a[href^='/product/']"));
            int before = listed.size();
            links.forEach(link -> listed.add(link.getAttribute("href").replaceAll(".*/product/", "")));
            broken.addAll(brokenImages().stream().map(src -> "/products?page=" + driver.getCurrentUrl().replaceAll(".*page=", "") + " " + src).toList());
            if (listed.size() == before) {
                break; // past the last page, the catalog shows the last page again
            }
        }
        assertEquals(forSale, listed, "the catalog pages list exactly the bikes for sale");

        for (String slug : forSale) {
            driver.get(baseUrl + "/product/" + slug);
            brokenImages().forEach(src -> broken.add("/product/" + slug + " " + src));
        }
        assertTrue(broken.isEmpty(), "photos that did not load:\n" + String.join("\n", broken));
    }

    @Test
    void retiredPetrolModelsHaveNoPage() throws Exception {
        for (String slug : RETIRED) {
            IllegalStateException missing = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> call(api, "GET", "/api/v1/products/" + slug, null));
            assertTrue(missing.getMessage().contains("404"), missing.getMessage());
        }
    }
}
