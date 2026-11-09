package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/**
 * V4: light / dark theme. Visitors keep their choice in the browser, signed-in customers in
 * {@code user_preferences.theme}; "system" follows the device. Screenshots of each page in dark mode go to
 * {@code target/selenium/theme-*.png} for a visual check.
 *
 * <pre>
 * mvn test -Dtest=ThemeSeleniumTest -Dselenium.baseUrl=http://localhost:8080
 * </pre>
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ThemeSeleniumTest extends StorefrontSeleniumTest {

    private static final By TOGGLE = By.cssSelector(".top .theme-toggle");

    @Test
    void newAccountsFollowTheDeviceAndSpeakVietnamese() throws Exception {
        JsonNode preferences = preferences();
        assertEquals("system", preferences.path("theme").asText());
        assertEquals("vi", preferences.path("language").asText());
        assertThrows(IllegalStateException.class,
            () -> call(api, "PUT", "/api/v1/users/" + userId() + "/preferences", Map.of("theme", "blue")),
            "only system, light or dark are accepted");
    }

    @Test
    void visitorCyclesTheThemeWithTheHeaderButtonAndTheBrowserRemembersIt() {
        driver.manage().deleteAllCookies();
        driver.get(baseUrl + "/");
        js("localStorage.removeItem('ebike-theme')");
        driver.navigate().refresh();
        assertEquals("system", theme());

        click(TOGGLE);
        assertEquals("light", theme());
        assertTrue(lightBackground(), "light theme has a light page: " + background());
        click(TOGGLE);
        assertEquals("dark", theme());
        assertTrue(!lightBackground(), "dark theme has a dark page: " + background());
        assertTrue(contrast() >= 4.5, "body text stays readable in dark mode: " + contrast());

        driver.get(baseUrl + "/products");
        assertEquals("dark", theme(), "the choice carries to the next page");
        assertNull(driver.findElement(By.tagName("html")).getAttribute("data-theme-user"), "a visitor's choice is not tied to an account");
        click(TOGGLE);
        assertEquals("system", theme(), "the button goes round: device → light → dark → device");
    }

    @Test
    void systemFollowsTheDeviceSetting() {
        driver.manage().deleteAllCookies();
        driver.get(baseUrl + "/");
        js("localStorage.removeItem('ebike-theme')");
        emulateDarkDevice(true);
        driver.navigate().refresh();
        assertEquals("system", theme());
        assertTrue(!lightBackground(), "a dark device gets the dark page: " + background());
        emulateDarkDevice(false);
        assertTrue(lightBackground(), "a light device gets the light page: " + background());
    }

    @Test
    void customerSavesTheThemeOnTheProfileAndItFollowsThemToAnotherBrowser() throws Exception {
        driver.get(baseUrl + "/customer/profile");
        WebElement choice = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("themeChoice")));
        assertEquals("system", new Select(choice).getFirstSelectedOption().getAttribute("value"));
        boolean notificationsBefore = preferences().path("notificationsEnabled").asBoolean();

        new Select(choice).selectByValue("dark");
        assertEquals("dark", theme());
        wait.until(d -> {
            try {
                return "dark".equals(preferences().path("theme").asText());
            } catch (Exception e) {
                return false;
            }
        });
        assertEquals(notificationsBefore, preferences().path("notificationsEnabled").asBoolean(),
            "saving the theme leaves the notification switch alone");

        // Another browser: nothing in localStorage, the server renders the saved theme into the page.
        js("localStorage.clear()");
        logIn(username, PASSWORD);
        driver.get(baseUrl + "/customer/orders");
        WebElement html = driver.findElement(By.tagName("html"));
        assertEquals("dark", html.getAttribute("data-theme"));
        assertEquals("true", html.getAttribute("data-theme-user"));
        assertTrue(!lightBackground(), "the account's dark theme is applied: " + background());

        // The header button saves to the account too.
        click(TOGGLE);
        assertEquals("system", theme());
        wait.until(d -> {
            try {
                return "system".equals(preferences().path("theme").asText());
            } catch (Exception e) {
                return false;
            }
        });
        driver.get(baseUrl + "/customer/profile");
        WebElement again = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("themeChoice")));
        assertEquals("system", new Select(again).getFirstSelectedOption().getAttribute("value"));
    }

    @Test
    void everyKindOfPageTurnsDark() throws Exception {
        openCheckoutWithOneBike(); // so the cart and checkout screenshots show a product photo
        js("localStorage.setItem('ebike-theme','dark')");
        call(api, "PUT", "/api/v1/users/" + userId() + "/preferences", Map.of("theme", "dark"));
        try {
            for (String page : List.of("/", "/products", "/product/" + productSlug, "/cart", "/checkout", "/support", "/chatbot",
                "/customer/orders", "/customer/profile")) {
                driver.get(baseUrl + page);
                wait.until(d -> "complete".equals(js("return document.readyState")));
                assertEquals("dark", theme(), page);
                assertTrue(!lightBackground(), page + " has a dark page: " + background());
                assertTrue(contrast() >= 4.5, page + " body text is readable: " + contrast());
                screenshot("theme-dark" + page.replaceAll("[^a-z0-9]+", "-"));
            }
            driver.manage().deleteAllCookies();
            for (String page : List.of("/login", "/signup")) {
                driver.get(baseUrl + page);
                assertEquals("dark", theme(), page);
                assertTrue(!lightBackground(), page + " has a dark page: " + background());
                // The button sits at the right end next to the cart, not in the middle of the bar.
                WebElement toggle = wait.until(ExpectedConditions.presenceOfElementLocated(TOGGLE));
                WebElement cart = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".top .cart-link")));
                long gap = (Long) js("return Math.round(arguments[0].getBoundingClientRect().left-arguments[1].getBoundingClientRect().right)", toggle, cart);
                assertTrue(gap >= 0 && gap < 60, page + ": the theme button follows the cart, gap " + gap + "px");
                screenshot("theme-dark" + page.replaceAll("[^a-z0-9]+", "-"));
            }
        } finally {
            call(api, "PUT", "/api/v1/users/" + userId() + "/preferences", Map.of("theme", "system"));
        }
    }

    @Test
    void productPhotosSitOnOneEvenLightTileInDarkModeOnly() throws Exception {
        String photo = "const f=document.querySelector('.detail-photo'),i=f.querySelector('img');"
            + "return getComputedStyle(f).backgroundColor+'|'+getComputedStyle(i).backgroundColor+'|'+getComputedStyle(i).filter";
        call(api, "PUT", "/api/v1/users/" + userId() + "/preferences", Map.of("theme", "dark"));
        try {
            driver.get(baseUrl + "/product/" + productSlug);
            assertEquals("rgb(235, 235, 235)|rgb(255, 255, 255)|brightness(0.92)", js(photo),
                "frame #ebebeb, photo white dimmed to the same tone");
            call(api, "PUT", "/api/v1/users/" + userId() + "/preferences", Map.of("theme", "light"));
            driver.navigate().refresh();
            assertEquals("light", theme());
            assertTrue(((String) js(photo)).endsWith("|none"), "the light theme keeps the photos as they were: " + js(photo));
        } finally {
            call(api, "PUT", "/api/v1/users/" + userId() + "/preferences", Map.of("theme", "system"));
        }
    }

    @Test
    void managerAndAdminPagesTurnDark() throws Exception {
        List<String[]> staff = new java.util.ArrayList<>();
        String manager = System.getProperty("selenium.managerUser", "");
        String admin = System.getProperty("selenium.adminUser", "");
        if (!manager.isBlank()) {
            staff.add(new String[] {manager, System.getProperty("selenium.managerPassword"), "/manager", "/manager/orders",
                "/manager/payments", "/manager/customers", "/manager/support", "/manager/inventory", "/manager/reports"});
        }
        if (!admin.isBlank()) {
            staff.add(new String[] {admin, System.getProperty("selenium.adminPassword"), "/admin", "/admin/accounts",
                "/admin/roles", "/admin/products", "/admin/pricing", "/admin/audit-logs", "/admin/inventory"});
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(!staff.isEmpty(), "no manager or admin account given");
        for (String[] account : staff) {
            logIn(account[0], account[1]);
            js("localStorage.setItem('ebike-theme','dark')");
            HttpClient client = newClient();
            call(client, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", account[0], "password", account[1]));
            long id = call(client, "GET", "/api/v1/auth/profile", null).path("userId").asLong();
            String saved = call(client, "GET", "/api/v1/users/" + id + "/preferences", null).path("theme").asText("system");
            call(client, "PUT", "/api/v1/users/" + id + "/preferences", Map.of("theme", "dark"));
            try {
                for (int i = 2; i < account.length; i++) {
                    String page = account[i];
                    driver.get(baseUrl + page);
                    wait.until(d -> "complete".equals(js("return document.readyState")));
                    // let the table or report finish rendering before the screenshot
                    wait.until(d -> Boolean.TRUE.equals(js("const l=document.querySelector('#loading');return !l||l.hidden")));
                    assertEquals("dark", theme(), page);
                    assertTrue(!lightBackground(), page + " has a dark page: " + background());
                    assertTrue(contrast() >= 4.5, page + " body text is readable: " + contrast());
                    screenshot("theme-dark" + page.replaceAll("[^a-z0-9]+", "-"));
                }
            } finally {
                call(client, "PUT", "/api/v1/users/" + id + "/preferences", Map.of("theme", saved));
            }
        }
    }

    // ---- steps ---------------------------------------------------------------------------

    private String theme() {
        return driver.findElement(By.tagName("html")).getAttribute("data-theme");
    }

    private static long userId() throws Exception {
        return call(api, "GET", "/api/v1/auth/profile", null).path("userId").asLong();
    }

    private static JsonNode preferences() throws Exception {
        return call(api, "GET", "/api/v1/users/" + userId() + "/preferences", null);
    }

    private void emulateDarkDevice(boolean dark) {
        ((ChromeDriver) driver).executeCdpCommand("Emulation.setEmulatedMedia",
            Map.of("features", List.of(Map.of("name", "prefers-color-scheme", "value", dark ? "dark" : "light"))));
    }

    /** The first non-transparent background from body up; transparent all the way means the canvas colour. */
    private static final String EFFECTIVE_BG = "const pick=el=>{for(;el;el=el.parentElement){const c=getComputedStyle(el).backgroundColor;"
        + "if(c&&!/rgba\\(.*, 0\\)$/.test(c)&&c!=='transparent')return c}return getComputedStyle(document.documentElement).colorScheme==='dark'?'rgb(18, 18, 18)':'rgb(255, 255, 255)'};";
    private static final String LUM = "const lum=c=>{const v=c.match(/[\\d.]+/g).slice(0,3).map(n=>{n/=255;return n<=0.03928?n/12.92:Math.pow((n+0.055)/1.055,2.4)});"
        + "return 0.2126*v[0]+0.7152*v[1]+0.0722*v[2]};";

    private String background() {
        return (String) js(EFFECTIVE_BG + "return pick(document.body)");
    }

    private boolean lightBackground() {
        return (Boolean) js(EFFECTIVE_BG + LUM + "return lum(pick(document.body))>0.5");
    }

    /** Contrast ratio of the body text against the page background. */
    private double contrast() {
        return ((Number) js(EFFECTIVE_BG + LUM + "const a=lum(getComputedStyle(document.body).color),b=lum(pick(document.body));"
            + "return (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05)")).doubleValue();
    }

    private void screenshot(String name) throws Exception {
        Path dir = Files.createDirectories(Path.of("target", "selenium"));
        Files.write(dir.resolve(name + ".png"), ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
    }
}
