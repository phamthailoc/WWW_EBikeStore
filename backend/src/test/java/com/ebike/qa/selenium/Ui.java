package com.ebike.qa.selenium;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Thin wrapper over WebDriver: explicit waits, instant scrolling, native dialogs. */
public class Ui {
    public final WebDriver d;
    public final WebDriverWait w;

    public Ui(WebDriver d) {
        this.d = d;
        this.w = new WebDriverWait(d, Duration.ofSeconds(15), Duration.ofMillis(100));
    }

    public WebDriverWait wait(int seconds) { return new WebDriverWait(d, Duration.ofSeconds(seconds), Duration.ofMillis(100)); }

    public void open(String path) { d.get(path.startsWith("http") ? path : QaConfig.baseUrl() + path); }

    public String url() { return d.getCurrentUrl(); }

    public String path() { return js("return location.pathname + location.search;").toString(); }

    public Object js(String script, Object... args) { return ((JavascriptExecutor) d).executeScript(script, args); }

    public WebElement visible(By by) { return w.until(ExpectedConditions.visibilityOfElementLocated(by)); }

    public WebElement present(By by) { return w.until(ExpectedConditions.presenceOfElementLocated(by)); }

    public List<WebElement> all(By by) { return d.findElements(by); }

    public boolean exists(By by) { return !d.findElements(by).isEmpty(); }

    public boolean shown(By by) { return d.findElements(by).stream().anyMatch(WebElement::isDisplayed); }

    public void scrollTo(WebElement e) { js("arguments[0].scrollIntoView({block:'center',behavior:'instant'});", e); }

    public WebElement reveal(By by) {
        WebElement e = present(by);
        scrollTo(e);
        return w.until(ExpectedConditions.visibilityOf(e));
    }

    public void click(By by) {
        for (int i = 0; ; i++) {
            try {
                WebElement e = reveal(by);
                w.until(ExpectedConditions.elementToBeClickable(e)).click();
                return;
            } catch (ElementClickInterceptedException | StaleElementReferenceException x) {
                if (i >= 3) { jsClick(by); return; }
                pause(150);
            }
        }
    }

    public void click(WebElement e) {
        scrollTo(e);
        js("arguments[0].click();", e);
    }

    public void jsClick(By by) { js("arguments[0].click();", present(by)); }

    public void type(By by, String text) {
        WebElement e = reveal(by);
        e.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, "a"), text.isEmpty() ? org.openqa.selenium.Keys.BACK_SPACE.toString() : text);
    }

    /** Sets a value from script and fires input and change (range, date and datetime-local inputs). */
    public void setValue(By by, String value) {
        js("const e=arguments[0];const s=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value').set;s.call(e,arguments[1]);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));", present(by), value);
    }

    public void selectValue(By by, String value) { new Select(visible(by)).selectByValue(value); }

    public void selectText(By by, String text) { new Select(visible(by)).selectByVisibleText(text); }

    public String selectedText(By by) { return new Select(visible(by)).getFirstSelectedOption().getText().trim(); }

    public String text(By by) { return reveal(by).getText().trim(); }

    public String textOf(By by) { return present(by).getDomProperty("textContent").replaceAll("\\s+", " ").trim(); }

    public String attr(By by, String name) { return present(by).getDomAttribute(name); }

    public String prop(By by, String name) { return String.valueOf(present(by).getDomProperty(name)); }

    public void waitText(By by, String part) {
        w.until(x -> {
            try { return x.findElement(by).getText().contains(part); } catch (RuntimeException e) { return false; }
        });
    }

    public void waitUrlContains(String part) { w.until(ExpectedConditions.urlContains(part)); }

    public <T> T until(Function<WebDriver, T> f) { return w.until(f); }

    public <T> T until(int seconds, Function<WebDriver, T> f) { return wait(seconds).until(f); }

    // ---- native dialogs ----
    public Alert alert() { return w.until(ExpectedConditions.alertIsPresent()); }

    public String acceptAlert() { Alert a = alert(); String t = a.getText(); a.accept(); return t; }

    public String dismissAlert() { Alert a = alert(); String t = a.getText(); a.dismiss(); return t; }

    /** Answers a prompt() with text (null = just OK) and returns the question that was asked. */
    public String answerPrompt(String text) {
        Alert a = alert();
        String t = a.getText();
        if (text != null) a.sendKeys(text);
        a.accept();
        return t;
    }

    public boolean hasAlert() {
        try { d.switchTo().alert(); return true; } catch (RuntimeException e) { return false; }
    }

    /** Fills the text box of the open site dialog (when text is not null) and presses its submit button. */
    public void dialogAnswer(String text) {
        if (text != null) type(By.cssSelector("dialog[open] textarea[name=text]"), text);
        click(By.cssSelector("dialog[open] [data-submit]"));
        until(d -> d.findElements(By.cssSelector("dialog[open]")).isEmpty() || d.findElements(By.cssSelector("dialog[open] .support-message:not([hidden])")).size() > 0);
    }

    public static void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public Path screenshot(String name) {
        try {
            Path dir = Path.of("target", "qa-selenium");
            Files.createDirectories(dir);
            File src = ((TakesScreenshot) d).getScreenshotAs(OutputType.FILE);
            Path out = dir.resolve(name.replaceAll("[^A-Za-z0-9._-]", "_") + ".png");
            Files.copy(src.toPath(), out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean noHorizontalScroll() {
        return Boolean.TRUE.equals(js("return document.documentElement.scrollWidth <= window.innerWidth + 1;"));
    }
}
