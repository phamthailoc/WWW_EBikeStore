package com.ebike.service.chatbot;

import com.ebike.config.chatbot.GeminiChatProperties;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class GeminiChatClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiChatClient.class);

    private final GeminiChatProperties properties;
    private final RestTemplate restTemplate;

    public GeminiChatClient(GeminiChatProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
            .build();
    }

    public boolean isConfigured() {
        return properties.isEnabled() && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    @SuppressWarnings("unchecked")
    public String generateAnswer(String prompt) {
        if (!isConfigured()) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
            )),
            "generationConfig", Map.of(
                "temperature", 0.35,
                "maxOutputTokens", 700
            )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        for (String model : models()) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + properties.getApiKey();
            for (int attempt = 1; attempt <= maxAttempts; attempt += 1) {
                try {
                    Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
                    return extractText(response);
                } catch (HttpStatusCodeException exception) {
                    if (isModelUnavailable(exception)) {
                        // Busy, rate limited or retired: retrying this model rarely helps, so try the next one now.
                        LOGGER.warn("Gemini model {} unavailable ({}); trying the next model.", model, exception.getStatusCode().value());
                        break;
                    }
                    if (!retry(model, attempt, maxAttempts, exception)) {
                        return null;
                    }
                } catch (RestClientException exception) {
                    // Network trouble (reset, timeout) affects every model, so retry the same one and stop after that.
                    if (!retry(model, attempt, maxAttempts, exception)) {
                        return null;
                    }
                }
            }
        }
        LOGGER.warn("No Gemini model was available; chatbot will use fallback response.");
        return null;
    }

    /** The main model first, then the fallback models, without blanks or repeats. */
    private List<String> models() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.add(properties.getModel());
        for (String fallback : properties.getFallbackModels()) {
            if (fallback != null && !fallback.isBlank()) {
                models.add(fallback.trim());
            }
        }
        return List.copyOf(models);
    }

    private static boolean isModelUnavailable(HttpStatusCodeException exception) {
        int status = exception.getStatusCode().value();
        return status == 503 || status == 429 || status == 404;
    }

    /** Logs the failure and waits before the next attempt; false when this was the last attempt. */
    private boolean retry(String model, int attempt, int maxAttempts, RestClientException exception) {
        if (attempt >= maxAttempts) {
            LOGGER.warn("Gemini model {} failed after {} attempt(s); chatbot will use fallback response.", model, attempt, exception);
            return false;
        }
        LOGGER.warn("Gemini model {} attempt {}/{} failed; retrying in {} ms.", model, attempt, maxAttempts, properties.getRetryDelayMs());
        sleepBeforeRetry();
        return true;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) {
            return null;
        }
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        Object text = parts.get(0).get("text");
        return text == null ? null : text.toString().trim();
    }

    private void sleepBeforeRetry() {
        long retryDelayMs = Math.max(0, properties.getRetryDelayMs());
        if (retryDelayMs == 0) {
            return;
        }
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
