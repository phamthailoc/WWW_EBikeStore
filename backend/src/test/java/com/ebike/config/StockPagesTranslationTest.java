package com.ebike.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/** V26: every phrase the stock pages show through t('...') has an English text, and the menu and page titles exist in both languages. */
class StockPagesTranslationTest {

    private static final Pattern PHRASE = Pattern.compile("\\bt\\('((?:[^'\\\\]|\\\\.)*)'");

    private static String read(String path) throws Exception {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"static/js/stockreceipts.js", "static/js/stock.js"})
    void everyPhraseOfTheScriptHasAnEnglishText(String script) throws Exception {
        Map<String, String> english = new ObjectMapper().readValue(read("i18n/js-en.json"), new TypeReference<>() { });
        Matcher matcher = PHRASE.matcher(read(script));
        List<String> missing = new ArrayList<>();
        int phrases = 0;
        while (matcher.find()) {
            phrases++;
            String phrase = matcher.group(1).replace("\\'", "'");
            if (!english.containsKey(phrase) || english.get(phrase).isBlank()) {
                missing.add(phrase);
            }
        }
        assertTrue(phrases > 0, script + " should use t('...')");
        assertTrue(missing.isEmpty(), "No English text in i18n/js-en.json for: " + missing);
    }

    @Test
    void thePageTitlesAndTheMenuEntryExistInBothLanguages() throws Exception {
        Properties vi = new Properties();
        Properties en = new Properties();
        try (InputStream stream = new ClassPathResource("messages.properties").getInputStream()) {
            vi.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        try (InputStream stream = new ClassPathResource("messages_en.properties").getInputStream()) {
            en.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        for (String key : List.of("staff.stockReceipts", "staff.stockReceiptsDesc", "staff.stockReceiptsMenu")) {
            assertTrue(!vi.getProperty(key, "").isBlank(), key + " is missing in Vietnamese");
            assertTrue(!en.getProperty(key, "").isBlank(), key + " is missing in English");
        }
    }
}
