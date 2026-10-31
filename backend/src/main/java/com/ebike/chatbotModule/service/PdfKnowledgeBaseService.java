package com.ebike.chatbotModule.service;

import com.ebike.chatbotModule.config.PdfKnowledgeProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class PdfKnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(PdfKnowledgeBaseService.class);
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Set<String> STOP_WORDS = Set.of(
        "ai", "anh", "bao", "ban", "cho", "co", "cua", "duoc", "gi", "giu", "hay", "khi", "khong",
        "la", "lam", "minh", "mot", "nao", "nay", "nhieu", "nguoi", "noi", "roi", "sao", "sau",
        "the", "thi", "toi", "tren", "va", "voi", "xe", "dien", "kinetic"
    );

    private final PdfKnowledgeProperties properties;
    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    private volatile List<PdfChunk> indexedChunks = List.of();

    public PdfKnowledgeBaseService(PdfKnowledgeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        if (!properties.isEnabled()) {
            log.info("PDF knowledge base is disabled");
            indexedChunks = List.of();
            return;
        }

        List<PdfChunk> loadedChunks = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources(properties.getLocationPattern());
            Arrays.sort(resources, Comparator.comparing(resource -> resource.getFilename() == null ? "" : resource.getFilename()));
            for (Resource resource : resources) {
                if (!resource.exists() || resource.getFilename() == null) {
                    continue;
                }
                loadedChunks.addAll(loadChunks(resource));
            }
        } catch (IOException exception) {
            log.warn("Unable to load PDF knowledge base", exception);
        }

        indexedChunks = List.copyOf(loadedChunks);
        log.info("Loaded {} PDF knowledge chunks", indexedChunks.size());
    }

    public PdfKnowledgeContext findRelevantContext(String userMessage) {
        if (!properties.isEnabled() || userMessage == null || userMessage.isBlank() || indexedChunks.isEmpty()) {
            return PdfKnowledgeContext.empty();
        }

        String normalizedQuery = normalize(userMessage);
        Set<String> queryTokens = tokenize(normalizedQuery);
        if (queryTokens.isEmpty()) {
            return PdfKnowledgeContext.empty();
        }

        List<KnowledgeSnippet> snippets = indexedChunks.stream()
            .map(chunk -> scoreChunk(chunk, normalizedQuery, queryTokens))
            .filter(scoredChunk -> scoredChunk.score() >= properties.getMinScore())
            .sorted(Comparator
                .comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(scoredChunk -> scoredChunk.chunk().sourceName())
                .thenComparingInt(scoredChunk -> scoredChunk.chunk().pageNumber()))
            .limit(properties.getMaxResults())
            .map(scoredChunk -> new KnowledgeSnippet(
                scoredChunk.chunk().sourceName(),
                scoredChunk.chunk().pageNumber(),
                scoredChunk.chunk().displayText()
            ))
            .toList();

        if (snippets.isEmpty()) {
            return PdfKnowledgeContext.empty();
        }

        String combinedContext = snippets.stream()
            .map(snippet -> "- Nguon: " + snippet.sourceName() + " | Trang: " + snippet.pageNumber() + "\n" + snippet.excerpt())
            .collect(Collectors.joining("\n\n"));

        return new PdfKnowledgeContext(combinedContext, snippets);
    }

    private List<PdfChunk> loadChunks(Resource resource) {
        List<PdfChunk> chunks = new ArrayList<>();
        String sourceName = buildSourceName(resource.getFilename());

        try (InputStream inputStream = resource.getInputStream(); PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = document.getNumberOfPages();
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String pageText = stripper.getText(document);
                chunks.addAll(chunkPage(sourceName, pageNumber, pageText));
            }
        } catch (IOException exception) {
            log.warn("Unable to read PDF document {}", sourceName, exception);
        }

        return chunks;
    }

    private List<PdfChunk> chunkPage(String sourceName, int pageNumber, String pageText) {
        String normalizedDisplayText = normalizeWhitespaceForDisplay(pageText);
        if (normalizedDisplayText.isBlank()) {
            return List.of();
        }

        List<PdfChunk> chunks = new ArrayList<>();
        int chunkSize = Math.max(250, properties.getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), chunkSize / 2));
        int start = 0;

        while (start < normalizedDisplayText.length()) {
            int end = Math.min(start + chunkSize, normalizedDisplayText.length());
            if (end < normalizedDisplayText.length()) {
                int boundary = normalizedDisplayText.lastIndexOf(' ', end);
                if (boundary > start + (chunkSize / 2)) {
                    end = boundary;
                }
            }

            String excerpt = normalizedDisplayText.substring(start, end).trim();
            if (excerpt.length() >= 80) {
                chunks.add(new PdfChunk(
                    sourceName,
                    pageNumber,
                    excerpt,
                    normalize(excerpt),
                    tokenize(normalize(excerpt))
                ));
            }

            if (end >= normalizedDisplayText.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }

        return chunks;
    }

    private ScoredChunk scoreChunk(PdfChunk chunk, String normalizedQuery, Set<String> queryTokens) {
        double score = 0;
        int overlapCount = 0;
        String normalizedSourceName = normalize(chunk.sourceName());

        if (chunk.normalizedText().contains(normalizedQuery)) {
            score += 12;
        }

        for (String token : queryTokens) {
            if (chunk.tokens().contains(token)) {
                overlapCount++;
                score += token.length() >= 5 ? 2.5 : 1.5;
            }
            if (normalizedSourceName.contains(token)) {
                score += 1.25;
            }
        }

        if (overlapCount == queryTokens.size()) {
            score += 6;
        } else if (overlapCount >= 2) {
            score += 2;
        }

        score += scoreDomainPhrase(normalizedQuery, chunk.normalizedText(), normalizedSourceName, "bao hanh", 8);
        score += scoreDomainPhrase(normalizedQuery, chunk.normalizedText(), normalizedSourceName, "doi tra", 7);
        score += scoreDomainPhrase(normalizedQuery, chunk.normalizedText(), normalizedSourceName, "hau mai", 5);
        score += scoreDomainPhrase(normalizedQuery, chunk.normalizedText(), normalizedSourceName, "thanh toan", 7);
        score += scoreDomainPhrase(normalizedQuery, chunk.normalizedText(), normalizedSourceName, "tra gop", 6);
        score += scoreDomainPhrase(normalizedQuery, chunk.normalizedText(), normalizedSourceName, "sac pin", 7);
        score += scoreDomainPhrase(normalizedQuery, chunk.normalizedText(), normalizedSourceName, "an toan", 5);

        return new ScoredChunk(chunk, score);
    }

    private double scoreDomainPhrase(String query, String text, String sourceName, String phrase, double weight) {
        if (!query.contains(phrase)) {
            return 0;
        }
        double score = 0;
        if (text.contains(phrase)) {
            score += weight;
        }
        if (sourceName.contains(phrase)) {
            score += weight / 2;
        }
        return score;
    }

    private String buildSourceName(String filename) {
        String baseName = filename == null ? "tai-lieu" : filename;
        if (baseName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        return baseName.replace('-', ' ');
    }

    private String normalizeWhitespaceForDisplay(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String mergedLines = repairMojibake(text).replace("\r", "")
            .replaceAll("(?<!\\n)\\n(?!\\n)", " ")
            .replaceAll("\\n{2,}", " ");
        return MULTI_SPACE.matcher(mergedLines).replaceAll(" ").trim();
    }

    private String repairMojibake(String text) {
        if (countMojibakeSignals(text) == 0) {
            return text;
        }
        String repaired = new String(text.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return countMojibakeSignals(repaired) < countMojibakeSignals(text) ? repaired : text;
    }

    private int countMojibakeSignals(String text) {
        int count = 0;
        String[] signals = {"Ã", "Ä", "Æ", "áº", "á»", "â", "Â"};
        for (String signal : signals) {
            int index = text.indexOf(signal);
            while (index >= 0) {
                count++;
                index = text.indexOf(signal, index + signal.length());
            }
        }
        return count;
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String withoutMarks = Normalizer.normalize(repairMojibake(text), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D');
        String cleaned = NON_ALPHANUMERIC.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return MULTI_SPACE.matcher(cleaned).replaceAll(" ").trim();
    }

    private Set<String> tokenize(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(normalizedText.split(" "))
            .map(String::trim)
            .filter(token -> token.length() >= 3)
            .filter(token -> !STOP_WORDS.contains(token))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record PdfChunk(
        String sourceName,
        int pageNumber,
        String displayText,
        String normalizedText,
        Set<String> tokens
    ) {
    }

    private record ScoredChunk(PdfChunk chunk, double score) {
    }

    public record KnowledgeSnippet(
        String sourceName,
        int pageNumber,
        String excerpt
    ) {
    }

    public record PdfKnowledgeContext(
        String combinedContext,
        List<KnowledgeSnippet> snippets
    ) {
        public static PdfKnowledgeContext empty() {
            return new PdfKnowledgeContext("", List.of());
        }

        public boolean hasSnippets() {
            return !snippets.isEmpty();
        }

        public List<String> sourceLabels() {
            return snippets.stream()
                .map(snippet -> snippet.sourceName() + " (trang " + snippet.pageNumber() + ")")
                .toList();
        }
    }
}
