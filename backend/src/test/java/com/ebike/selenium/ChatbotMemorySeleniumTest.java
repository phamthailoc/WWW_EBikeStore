package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

/** V9: the chatbot keeps one conversation per tab, so a follow-up question is about the bikes it just suggested. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ChatbotMemorySeleniumTest extends StorefrontSeleniumTest {

    private static final By ANSWERS = By.cssSelector("#chatlog .chat-answer");

    private String ask(WebDriverWait slow, String question) {
        int before = driver.findElements(ANSWERS).size();
        fill("message", question);
        click(By.cssSelector("#chatForm button"));
        slow.until(d -> d.findElements(ANSWERS).size() > before);
        List<WebElement> answers = driver.findElements(ANSWERS);
        return answers.get(answers.size() - 1).getText();
    }

    @Test
    void aFollowUpQuestionIsAboutTheSuggestedBikes() {
        // Gemini may retry for a while before the chatbot falls back to its own answer.
        WebDriverWait slow = new WebDriverWait(driver, Duration.ofSeconds(90));
        driver.get(baseUrl + "/chatbot");
        slow.until(d -> !d.findElements(By.id("chatForm")).isEmpty());

        ask(slow, "Gợi ý xe điện đi học giá dưới 20 triệu");
        WebElement card = slow.until(d -> d.findElements(By.cssSelector("#chatlog .chat-cards li a")).stream().findFirst().orElse(null));
        assertTrue(card.getAttribute("href").contains("/product/"), "suggested bikes are links: " + card.getAttribute("href"));
        String firstBike = card.getText().trim();
        String chatId = (String) js("return sessionStorage.getItem('kinetic_chat_id')");
        assertTrue(chatId != null && !chatId.isBlank(), "the tab keeps a conversation id");

        String answer = ask(slow, "Xe đầu tiên giá bao nhiêu?");
        assertTrue(answer.contains(firstBike), "the follow-up answer is about " + firstBike + ": " + answer);
        assertEquals(chatId, js("return sessionStorage.getItem('kinetic_chat_id')"), "the same conversation id is reused");
    }
}
