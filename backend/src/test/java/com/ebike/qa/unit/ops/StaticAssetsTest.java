package com.ebike.qa.unit.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Static assets the pages depend on: CSS @import rules must lead the file, and every picture a stylesheet names must ship. */
class StaticAssetsTest {

    private static final Pattern IMAGE_URL = Pattern.compile("url\\(\\s*['\"]?(/images/[^'\")\\s?#]+)");

    @Test
    void noCssFileHasAnImportAfterItsFirstRule() throws IOException {
        Resource[] sheets = new PathMatchingResourcePatternResolver().getResources("classpath:static/css/*.css");
        assertThat(sheets).isNotEmpty();
        for (Resource sheet : sheets) {
            String css = new String(sheet.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(importAfterRule(css)).as("@import after a rule in %s is ignored by browsers", sheet.getFilename()).isFalse();
        }
    }

    @Test
    void everyImageNamedByAStylesheetExists() throws IOException {
        Resource[] sheets = new PathMatchingResourcePatternResolver().getResources("classpath:static/css/*.css");
        List<String> missing = new ArrayList<>();
        for (Resource sheet : sheets) {
            String css = new String(sheet.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String path : imagePaths(css)) {
                if (!new ClassPathResource("static" + path).exists()) {
                    missing.add(sheet.getFilename() + " -> " + path);
                }
            }
        }
        assertThat(missing).as("images named in CSS but not shipped").isEmpty();
    }

    @Test
    void checkerFindsALateImport() {
        assertThat(importAfterRule(".a{color:red}\n@import url('x.css');")).isTrue();
        assertThat(importAfterRule("@import url('x.css');\n.a{color:red}")).isFalse();
    }

    @Test
    void checkerIgnoresBomCommentsCharsetAndLayerStatements() {
        assertThat(importAfterRule("﻿@charset \"utf-8\";\n/* note */ @layer base, theme;\n@import url('x.css');\n.a{}")).isFalse();
        assertThat(importAfterRule("/* @import url('x.css'); */ .a{}")).isFalse();
    }

    @Test
    void checkerEndsAnImportWithoutUrlAtItsSemicolon() {
        assertThat(importAfterRule("@import \"a.css\";\n@import url('b;c.css');\n.a{}")).isFalse();
        assertThat(importAfterRule("@import \"a.css\";\n.a{}\n@import \"b.css\";")).isTrue();
    }

    @Test
    void checkerIgnoresImportAndCommentMarkersInsideStrings() {
        assertThat(importAfterRule(".a::before{content:\"@import /* x\"}")).isFalse();
        assertThat(importAfterRule(".a::before{content:\"/*\"}\n.b{}\n@import url('x.css');")).isTrue();
    }

    @Test
    void imageExtractionReadsQuotedAndBareUrls() {
        assertThat(imagePaths(".a{background:url('/images/a.png') center}.b{background:url(\"/images/b/c.jpg\")}.c{background:url(/images/d.svg?v=1)}.d{background:url(https://x.test/e.png)}"))
                .containsExactly("/images/a.png", "/images/b/c.jpg", "/images/d.svg");
    }

    /** True when an @import statement appears after the first rule (browsers then ignore it). */
    static boolean importAfterRule(String source) {
        String css = source.startsWith("﻿") ? source.substring(1) : source;
        int pos = 0;
        boolean seenRule = false;
        int n = css.length();
        while (pos < n) {
            char c = css.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else if (css.startsWith("/*", pos)) {
                int end = css.indexOf("*/", pos + 2);
                pos = end < 0 ? n : end + 2;
            } else if (c == '"' || c == '\'') {
                pos = endOfString(css, pos);
            } else if (css.startsWith("@import", pos)) {
                if (seenRule) {
                    return true;
                }
                pos = endOfStatement(css, pos + 7);
            } else if (!seenRule && (css.startsWith("@charset", pos) || css.startsWith("@layer", pos)) && isStatement(css, pos)) {
                pos = endOfStatement(css, pos);
            } else {
                seenRule = true;
                pos++;
            }
        }
        return false;
    }

    /** An @layer / @charset is a statement (ends with ';') only when no '{' comes first. */
    private static boolean isStatement(String css, int pos) {
        int semi = css.indexOf(';', pos);
        int brace = css.indexOf('{', pos);
        return semi >= 0 && (brace < 0 || semi < brace);
    }

    /** End of a statement: skips quoted strings and parentheses, stops after the ';'. */
    private static int endOfStatement(String css, int pos) {
        int depth = 0;
        int n = css.length();
        while (pos < n) {
            char c = css.charAt(pos);
            if (c == '"' || c == '\'') {
                pos = endOfString(css, pos);
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == ';' && depth == 0) {
                return pos + 1;
            }
            pos++;
        }
        return n;
    }

    private static int endOfString(String css, int start) {
        char quote = css.charAt(start);
        int pos = start + 1;
        while (pos < css.length() && css.charAt(pos) != quote) {
            pos += css.charAt(pos) == '\\' ? 2 : 1;
        }
        return Math.min(pos + 1, css.length());
    }

    /** Local picture paths (/images/...) named by url(...) in a stylesheet, comments ignored. */
    static List<String> imagePaths(String css) {
        List<String> paths = new ArrayList<>();
        Matcher m = IMAGE_URL.matcher(css.replaceAll("(?s)/\\*.*?\\*/", ""));
        while (m.find()) {
            paths.add(m.group(1));
        }
        return paths;
    }
}
