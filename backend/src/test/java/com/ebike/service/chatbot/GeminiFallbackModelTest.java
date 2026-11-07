package com.ebike.service.chatbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ebike.config.chatbot.GeminiChatProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

/** When Google says the main model is busy or gone, the chatbot asks a fallback model before giving up on AI. */
class GeminiFallbackModelTest {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String ANSWER = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"%s\"}]}}]}";

    private GeminiChatProperties properties;
    private MockServerRestTemplateCustomizer customizer;

    @BeforeEach
    void setUp() {
        properties = new GeminiChatProperties();
        properties.setApiKey("test-key");
        properties.setModel("main-model");
        properties.setFallbackModels(List.of("spare-model"));
        properties.setMaxAttempts(3);
        properties.setRetryDelayMs(0);
        customizer = new MockServerRestTemplateCustomizer();
    }

    private GeminiChatClient client() {
        return new GeminiChatClient(properties, new RestTemplateBuilder(customizer));
    }

    private static String url(String model) {
        return BASE + model + ":generateContent?key=test-key";
    }

    @Test
    void anOverloadedMainModelHandsOverToTheFallbackWithoutRetrying() {
        GeminiChatClient gemini = client();
        MockRestServiceServer server = customizer.getServer();
        server.expect(ExpectedCount.once(), requestTo(url("main-model"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.once(), requestTo(url("spare-model")))
            .andRespond(withSuccess(ANSWER.formatted("from spare"), MediaType.APPLICATION_JSON));

        assertEquals("from spare", gemini.generateAnswer("hi"));
        server.verify();
    }

    @Test
    void aModelThatIsNoLongerOfferedAlsoHandsOver() {
        GeminiChatClient gemini = client();
        MockRestServiceServer server = customizer.getServer();
        server.expect(ExpectedCount.once(), requestTo(url("main-model"))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(ExpectedCount.once(), requestTo(url("spare-model")))
            .andRespond(withSuccess(ANSWER.formatted("from spare"), MediaType.APPLICATION_JSON));

        assertEquals("from spare", gemini.generateAnswer("hi"));
        server.verify();
    }

    @Test
    void aHealthyMainModelNeverCallsTheFallback() {
        GeminiChatClient gemini = client();
        MockRestServiceServer server = customizer.getServer();
        server.expect(ExpectedCount.once(), requestTo(url("main-model")))
            .andRespond(withSuccess(ANSWER.formatted("from main"), MediaType.APPLICATION_JSON));

        assertEquals("from main", gemini.generateAnswer("hi"));
        server.verify();
    }

    @Test
    void whenEveryModelIsBusyTheChatbotFallsBackToItsOwnAnswer() {
        GeminiChatClient gemini = client();
        MockRestServiceServer server = customizer.getServer();
        server.expect(ExpectedCount.once(), requestTo(url("main-model"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.once(), requestTo(url("spare-model"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertNull(gemini.generateAnswer("hi"));
        server.verify();
    }
}
