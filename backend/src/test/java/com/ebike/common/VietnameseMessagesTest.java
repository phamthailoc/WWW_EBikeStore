package com.ebike.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The shop speaks Vietnamese only: no message a client can read is written in English. */
class VietnameseMessagesTest {
    private static final Pattern THROWN = Pattern.compile("new ResponseStatusException\\(\\s*HttpStatus\\.[A-Z_]+,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern VIETNAMESE = Pattern.compile("[àáảãạăắằẳẵặâấầẩẫậđèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵ]", Pattern.CASE_INSENSITIVE);

    @Test
    void noErrorTextOfTheServerIsEnglish() throws IOException {
        Path root = Path.of("src/main/java");
        List<String> english = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = THROWN.matcher(source);
                while (matcher.find()) {
                    String text = matcher.group(1);
                    boolean hasWords = text.matches(".*[A-Za-z]{3,} [A-Za-z]{2,}.*");
                    if (hasWords && !VIETNAMESE.matcher(text).find()) {
                        english.add(root.relativize(file) + ": " + text);
                    }
                }
            }
        }
        assertEquals(List.of(), english, "error texts that are not Vietnamese");
    }
}
