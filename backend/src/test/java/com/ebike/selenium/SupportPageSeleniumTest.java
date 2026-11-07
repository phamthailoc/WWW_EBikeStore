package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** V15: the support page shows the FAQ with today's fees, takes a request, and the manager sees it. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class SupportPageSeleniumTest extends StorefrontSeleniumTest {

    private static final By ROWS = By.cssSelector("#data-table tbody tr");

    @Test
    void customerSendsARequestThatTheManagerCanRead() {
        driver.get(baseUrl + "/support");
        WebElement fee = wait.until(ExpectedConditions.presenceOfElementLocated(
            By.xpath("//details[summary[normalize-space()='Phí đăng ký là bao nhiêu?']]")));
        String answer = (String) js("return arguments[0].querySelector('p').textContent", fee);
        assertTrue(answer.contains("VND") && !answer.contains("{"), "the FAQ answer has today's fee filled in: " + answer);

        WebElement form = driver.findElement(By.id("supportForm"));
        wait.until(d -> !form.findElement(By.name("email")).getAttribute("value").isBlank());
        assertTrue(form.findElement(By.name("email")).getAttribute("value").startsWith(username), "signed-in email is filled in");

        String subject = "Selenium hỏi bảo hành " + System.currentTimeMillis();
        fill("subject", subject);
        fill("message", "Xe của tôi cần kiểm tra pin, tôi mang tới cửa hàng được không?");
        // A plain click landed outside the viewport here (the button was not under the pointer), so submit the form itself.
        js("document.querySelector('#supportForm').requestSubmit()");
        WebElement message = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#supportForm .support-message")));
        wait.until(d -> !message.getText().isBlank());
        assertFalse(message.getAttribute("class").contains("error"), "the request is accepted: " + message.getText());

        String manager = System.getProperty("selenium.managerUser", "");
        assumeTrue(!manager.isBlank(), "selenium.managerUser not set; manager list not checked");
        logIn(manager, System.getProperty("selenium.managerPassword"));
        driver.get(baseUrl + "/manager/support");
        wait.until(d -> d.findElements(ROWS).stream().anyMatch(row -> row.getText().contains(subject)));
    }
}
