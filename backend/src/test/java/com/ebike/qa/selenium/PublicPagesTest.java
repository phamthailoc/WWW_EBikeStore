package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class PublicPagesTest extends QaBase {
    private static final By LOOKUP_INPUT = By.cssSelector("#warrantyLookupForm [name=frame]");
    private static final By LOOKUP_RESULT = By.id("warrantyLookupResult");

    private void lookup(String frame) {
        ui.type(LOOKUP_INPUT, frame);
        ui.click(By.cssSelector("#warrantyLookupForm button[type=submit]"));
    }

    // ---------------------------------------------------------------- warranty lookup

    @Test
    void warrantyLookupAsksForAFrameNumber() {
        ui.open("/warranty");
        ui.visible(LOOKUP_INPUT);
        ui.js("document.querySelector('#warrantyLookupForm').requestSubmit();");
        ui.waitText(LOOKUP_RESULT, "Vui lòng nhập số khung.");
    }

    @Test
    void warrantyLookupOfAnUnknownFrameShowsAnError() {
        ui.open("/warranty");
        lookup("NOSUCHFRAME" + System.nanoTime() % 100000);
        ui.until(d -> ui.exists(By.cssSelector("#warrantyLookupResult .notice.error")));
        assertFalse(ui.text(By.cssSelector("#warrantyLookupResult .notice.error")).isBlank());
        assertFalse(ui.exists(By.cssSelector(".warranty-card")));
    }

    @Test
    void warrantyLookupOfADeliveredBikeShowsCoverageButNoOwnerData() {
        String frame = Flow.deliver(Flow.guestOrder("PAY_LATER"));
        ui.open("/warranty");
        lookup(frame);
        ui.visible(By.cssSelector(".warranty-card"));
        assertEquals(frame, ui.text(By.cssSelector("[data-field=frameNumber]")));
        assertEquals("Còn bảo hành", ui.text(By.cssSelector("[data-field=coverage]")));
        assertTrue(ui.text(By.cssSelector("[data-field=productName]")).contains(Shop.buyName()));
        assertTrue(ui.url().contains("frame=" + frame), "the lookup is shareable by link");
        String card = ui.text(By.cssSelector(".warranty-card")).toLowerCase();
        assertFalse(card.contains("@") || card.contains("khách kiểm thử"), "no owner data: " + card);
        // opening the shared link repeats the lookup
        ui.open("/warranty?frame=" + frame);
        ui.visible(By.cssSelector(".warranty-card"));
        assertEquals(frame, ui.text(By.cssSelector("[data-field=frameNumber]")));
    }

    // ---------------------------------------------------------------- support page

    @Test
    void supportPageListsFaqAndShowrooms() {
        ui.open("/support");
        ui.visible(By.id("supportForm"));
        JsonNode faq = new Api().get("/api/v1/faq").json();
        long showrooms = new Api().get("/api/v1/showrooms").json().size();
        assertEquals(faq.size(), ui.all(By.cssSelector(".faq-group details")).size());
        assertEquals(showrooms, ui.all(By.cssSelector(".flow-card")).size());
        if (faq.size() > 0) {
            var first = ui.all(By.cssSelector(".faq-group details")).get(0);
            ui.click(first.findElement(By.tagName("summary")));
            assertEquals("true", first.getDomAttribute("open"));
        }
    }

    @Test
    void supportFormIsValidatedByTheBrowser() {
        ui.open("/support");
        By form = By.id("supportForm");
        ui.visible(form);
        ui.js("document.querySelector('#supportForm').requestSubmit();");
        assertFalse(ui.shown(By.cssSelector(".support-message")), "empty form is blocked");
        ui.type(By.cssSelector("#supportForm [name=customerName]"), "Khách Hỏi");
        ui.type(By.cssSelector("#supportForm [name=email]"), "abc");
        ui.type(By.cssSelector("#supportForm [name=subject]"), "Hỏi");
        ui.type(By.cssSelector("#supportForm [name=message]"), "ngắn");
        assertEquals(Boolean.TRUE, ui.js("return arguments[0].validity.typeMismatch;", ui.present(By.cssSelector("#supportForm [name=email]"))));
        assertEquals(Boolean.TRUE, ui.js("return arguments[0].validity.tooShort;", ui.present(By.cssSelector("#supportForm [name=message]"))));
    }

    @Test
    void supportRequestIsSentAndReachesTheManager() {
        String subject = "Hỏi giá pin " + System.nanoTime();
        ui.open("/support");
        ui.type(By.cssSelector("#supportForm [name=customerName]"), "Khách Hỏi Giá");
        ui.type(By.cssSelector("#supportForm [name=email]"), "hoi.gia@example.com");
        ui.type(By.cssSelector("#supportForm [name=phone]"), "0901234567");
        ui.type(By.cssSelector("#supportForm [name=subject]"), subject);
        ui.type(By.cssSelector("#supportForm [name=message]"), "Pin thay thế cho xe S One giá bao nhiêu và bảo hành thế nào?");
        ui.click(By.cssSelector("#supportForm button[type=submit]"));
        ui.waitText(By.cssSelector(".support-message"), "Đã gửi yêu cầu. Cửa hàng sẽ phản hồi qua email của bạn.");
        assertEquals("", ui.prop(By.cssSelector("#supportForm [name=subject]"), "value"), "subject is cleared after sending");
        JsonNode list = Api.staff("manager").get("/api/v1/manager/support-requests").json();
        boolean found = false;
        for (JsonNode r : list) found |= subject.equals(r.get("subject").asText());
        assertTrue(found, "the manager sees the request");
    }

    @Test
    void supportFormIsPrefilledForASignedInCustomer() {
        Customer c = newCustomer();
        loginCustomer(c);
        ui.open("/support");
        ui.until(d -> !ui.prop(By.cssSelector("#supportForm [name=email]"), "value").isEmpty());
        assertEquals(c.email(), ui.prop(By.cssSelector("#supportForm [name=email]"), "value"));
        assertEquals("Kiểm Thử", ui.prop(By.cssSelector("#supportForm [name=customerName]"), "value"));
    }

    // ---------------------------------------------------------------- chatbot

    @Test
    void chatbotAnswersAFaqQuestion() {
        ui.open("/chatbot");
        ui.visible(By.id("chatForm"));
        ui.type(By.cssSelector("#chatForm [name=message]"), "Xe có được bảo hành bao lâu?");
        ui.click(By.cssSelector("#chatForm button"));
        ui.until(20, d -> ui.exists(By.cssSelector("#chatlog .chat-answer")));
        assertEquals("Xe có được bảo hành bao lâu?", ui.text(By.cssSelector("#chatlog .chat-user")));
        String answer = ui.text(By.cssSelector("#chatlog .chat-answer"));
        assertTrue(answer.length() > 10 && !answer.startsWith("{"), "a readable answer, not raw JSON: " + answer);
        assertEquals("", ui.prop(By.cssSelector("#chatForm [name=message]"), "value"), "the box is cleared");
    }

    @Test
    void chatbotRefusesABlankQuestion() {
        ui.open("/chatbot");
        ui.visible(By.id("chatForm"));
        ui.js("document.querySelector('#chatForm').requestSubmit();");
        assertEquals(0, ui.all(By.cssSelector("#chatlog .chat-user")).size());
        assertEquals("true", ui.prop(By.cssSelector("#chatForm [name=message]"), "required"));
    }

    @Test
    void chatbotRecommendsBikesAsLinks() {
        ui.open("/chatbot");
        ui.type(By.cssSelector("#chatForm [name=message]"), "Gợi ý xe đi làm giá dưới 20 triệu");
        ui.click(By.cssSelector("#chatForm button"));
        ui.until(30, d -> ui.exists(By.cssSelector("#chatlog .chat-answer")));
        for (var a : ui.all(By.cssSelector("#chatlog .chat-cards a"))) assertTrue(a.getDomAttribute("href").startsWith("/product/"));
    }

    @Test
    void comparePageWithoutBikesExplainsItself() {
        ui.open("/compare");
        ui.until(d -> ui.textOf(By.id("flow-extra")).contains("Chưa có mẫu xe để so sánh."));
    }

    @Test
    void favouritesPageSendsAGuestToLogin() {
        ui.open("/favorites");
        ui.until(d -> d.getCurrentUrl().contains("/login"));
    }
}
