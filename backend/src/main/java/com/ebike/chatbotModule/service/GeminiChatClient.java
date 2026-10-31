package com.ebike.chatbotModule.service;

import com.ebike.chatbotModule.config.GeminiChatProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
            + properties.getModel()
            + ":generateContent?key="
            + properties.getApiKey();

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

        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt += 1) {
            try {
                Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<>(requestBody, headers), Map.class);
                return extractText(response);
            } catch (RestClientException exception) {
                if (attempt >= maxAttempts) {
                    LOGGER.warn("Gemini API call failed after {} attempt(s); chatbot will use fallback response.", attempt, exception);
                    return null;
                }
                LOGGER.warn("Gemini API call attempt {}/{} failed; retrying in {} ms.", attempt, maxAttempts, properties.getRetryDelayMs());
                sleepBeforeRetry();
            }
        }
        return null;
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
