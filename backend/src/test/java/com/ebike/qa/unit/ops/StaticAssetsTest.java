package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Static assets the pages depend on: CSS @import rules must lead the file, and the home hero picture must ship. */
class StaticAssetsTest {

    @Test
    void noCssFileHasAnImportAfterItsFirstRule() throws IOException {
        Resource[] sheets = new PathMatchingResourcePatternResolver().getResources("classpath:static/css/*.css");
        assertThat(sheets).isNotEmpty();
        for (Resource sheet : sheets) {
            String css = new String(sheet.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", "");
            int pos = 0;
            while (pos < css.length()) {
                while (pos < css.length() && Character.isWhitespace(css.charAt(pos))) {
                    pos++;
                }
                if (pos >= css.length()) {
                    break;
                }
                if (css.startsWith("@charset", pos) || css.startsWith("@import", pos)) {
                    // the url(...) may itself contain ';' (Google Fonts), so skip past its closing parenthesis first
                    int end = css.indexOf(';', Math.max(pos, css.indexOf(')', pos)));
                    if (end < 0) {
                        break;
                    }
                    pos = end + 1;
                } else {
                    // first real rule: nothing after this point may be an @import
                    assertThat(css.substring(pos)).as("@import after a rule in %s is ignored by browsers", sheet.getFilename())
                            .doesNotContain("@import");
                    break;
                }
            }
        }
    }

    @Test
    void homeHeroBannerIsOnTheClasspath() throws IOException {
        ClassPathResource banner = new ClassPathResource("static/images/Kinetic-Banner.jpg");
        assertThat(banner.exists()).isTrue();
        assertThat(banner.contentLength()).isGreaterThan(10_000);
    }
}
