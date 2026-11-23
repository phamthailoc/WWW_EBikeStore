package com.ebike.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/** Spring Boot reads .properties files as ISO-8859-1, so Vietnamese defaults must be written as unicode escapes. */
class ApplicationPropertiesEncodingTest {

    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

    @Test
    void storeDetailsKeepTheirVietnameseLetters() throws IOException {
        List<PropertySource<?>> sources = new PropertiesPropertySourceLoader()
            .load("application", new FileSystemResource(MAIN_RESOURCES.resolve("application.properties")));
        PropertySource<?> source = sources.get(0);

        assertEquals("${APP_STORE_LEGAL_NAME:Công ty TNHH Kinetic E-Bike (mô phỏng)}", source.getProperty("app.store.legal-name"));
        assertEquals("${APP_STORE_ADDRESS:12 Nguyễn Văn Linh, Quận 7, TP. Hồ Chí Minh}", source.getProperty("app.store.address"));
    }

    @Test
    void propertiesFilesContainOnlyAscii() throws IOException {
        try (var files = Files.list(MAIN_RESOURCES)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("application.*[.]properties")).toList()) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                assertTrue(text.chars().allMatch(c -> c < 128), file + " has non-ASCII characters; use unicode escapes");
            }
        }
    }
}
