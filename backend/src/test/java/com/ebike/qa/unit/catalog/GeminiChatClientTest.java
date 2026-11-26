package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ebike.config.chatbot.GeminiChatProperties;
import com.ebike.service.chatbot.GeminiChatClient;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GeminiChatClientTest {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private GeminiChatProperties properties;
    private GeminiChatClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new GeminiChatProperties();
        properties.setApiKey("KEY");
        properties.setModel("main");
        properties.setFallbackModels(new java.util.ArrayList<>(List.of("fallback")));
        properties.setRetryDelayMs(0);
        properties.setMaxAttempts(3);
        client = new GeminiChatClient(properties, new RestTemplateBuilder());
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
    }

    private static String url(String model) {
        return BASE + model + ":generateContent?key=KEY";
    }

    private static String ok(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + new com.fasterxml.jackson.databind.node.TextNode(text) + "}]}}]}";
    }

    @Test
    void notConfiguredWithoutKeyOrWhenDisabledAndNoRequestIsSent() {
        properties.setApiKey(null);
        assertThat(client.isConfigured()).isFalse();
        assertThat(client.generateAnswer("hi")).isNull();
        properties.setApiKey("   ");
        assertThat(client.isConfigured()).isFalse();
        properties.setApiKey("KEY");
        properties.setEnabled(false);
        assertThat(client.isConfigured()).isFalse();
        assertThat(client.generateAnswer("hi")).isNull();
        server.verify();
    }

    @Test
    void successfulAnswerIsTrimmedAndTheRequestCarriesThePromptAndKey() {
        server.expect(once(), requestTo(url("main"))).andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("câu hỏi \\\"quote\\\"")))
            .andRespond(withSuccess(ok("  Xin chào  "), MediaType.APPLICATION_JSON));
        assertThat(client.generateAnswer("câu hỏi \"quote\"")).isEqualTo("Xin chào");
        server.verify();
    }

    @Test
    void busyRateLimitedOrRetiredMainModelFallsBackToTheNextModelWithoutRetrying() {
        for (HttpStatus status : new HttpStatus[] {HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.NOT_FOUND}) {
            server.reset();
            server.expect(once(), requestTo(url("main"))).andRespond(withStatus(status));
            server.expect(once(), requestTo(url("fallback"))).andRespond(withSuccess(ok("from fallback"), MediaType.APPLICATION_JSON));
            assertThat(client.generateAnswer("q")).as(status.toString()).isEqualTo("from fallback");
            server.verify();
        }
    }

    @Test
    void allModelsUnavailableGivesNullSoTheChatbotCanFallBack() {
        server.expect(once(), requestTo(url("main"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(url("fallback"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(client.generateAnswer("q")).isNull();
        server.verify();
    }

    @Test
    void serverErrorIsRetriedOnTheSameModelAndThenSucceeds() {
        server.expect(times(2), requestTo(url("main"))).andRespond(withServerError());
        server.expect(once(), requestTo(url("main"))).andRespond(withSuccess(ok("third time"), MediaType.APPLICATION_JSON));
        assertThat(client.generateAnswer("q")).isEqualTo("third time");
        server.verify();
    }

    @Test
    void serverErrorAfterAllAttemptsGivesNullAndDoesNotTouchTheFallbackModel() {
        server.expect(times(3), requestTo(url("main"))).andRespond(withServerError());
        assertThat(client.generateAnswer("q")).isNull();
        server.verify();
    }

    @Test
    void networkFailureIsRetriedThenGivesUp() {
        server.expect(times(3), requestTo(url("main"))).andRespond(request -> {
            throw new IOException("connection reset");
        });
        assertThat(client.generateAnswer("q")).isNull();
        server.verify();
    }

    @Test
    void maxAttemptsBelowOneStillMakesOneAttempt() {
        properties.setMaxAttempts(0);
        server.expect(once(), requestTo(url("main"))).andRespond(withServerError());
        assertThat(client.generateAnswer("q")).isNull();
        server.verify();
    }

    @Test
    void blankAndDuplicateFallbackModelsAreSkipped() {
        properties.setFallbackModels(new java.util.ArrayList<>(java.util.Arrays.asList("main", " ", null, " other ")));
        server.expect(once(), requestTo(url("main"))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(url("other"))).andRespond(withSuccess(ok("ok"), MediaType.APPLICATION_JSON));
        assertThat(client.generateAnswer("q")).isEqualTo("ok");
        server.verify();
    }

    @Test
    void malformedOrEmptyResponsesGiveNullNotAnException() {
        String[] bodies = {"{}", "{\"candidates\":[]}", "{\"candidates\":[{}]}", "{\"candidates\":[{\"content\":{}}]}",
            "{\"candidates\":[{\"content\":{\"parts\":[]}}]}", "{\"candidates\":[{\"content\":{\"parts\":[{}]}}]}"};
        for (String body : bodies) {
            server.reset();
            server.expect(once(), requestTo(url("main"))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThat(client.generateAnswer("q")).as(body).isNull();
        }
    }

    /**
     * Expected: a 400/401/403 (bad request, bad API key) can never succeed on retry, so the client gives up at once instead of
     * burning three attempts (and three retry delays of 3 s by default) on every chat message. Actual: any other HTTP error is
     * retried maxAttempts times. GeminiChatClient.java:66-74.
     */
    @Test
    @Tag("bug")
    void bug_clientErrorsLikeBadApiKeyAreRetried() {
        server.expect(once(), requestTo(url("main"))).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(client.generateAnswer("q")).isNull();
        server.verify();
    }
}
