package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.config.chatbot.PdfKnowledgeProperties;
import com.ebike.service.chatbot.PdfKnowledgeBaseService;
import com.ebike.service.chatbot.PdfKnowledgeBaseService.PdfKnowledgeContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfKnowledgeBaseServiceTest {

    private static PdfKnowledgeBaseService real(PdfKnowledgeProperties properties) {
        PdfKnowledgeBaseService service = new PdfKnowledgeBaseService(properties);
        service.initialize();
        return service;
    }

    // ---- the PDFs shipped with the shop

    @Test
    void shippedPdfsAreIndexedAndWarrantyQuestionsFindTheWarrantyDocument() {
        PdfKnowledgeContext context = real(new PdfKnowledgeProperties()).findRelevantContext("Chính sách bảo hành xe như thế nào?");
        assertThat(context.hasSnippets()).isTrue();
        assertThat(context.snippets().size()).isLessThanOrEqualTo(3);
        assertThat(context.sourceLabels()).anyMatch(label -> label.contains("bao hanh"));
        assertThat(context.combinedContext()).contains("Nguon:").contains("Trang:");
    }

    @Test
    void diacriticsDoNotMatterWhenSearchingThePdfs() {
        PdfKnowledgeBaseService service = real(new PdfKnowledgeProperties());
        assertThat(service.findRelevantContext("huong dan sac pin an toan").sourceLabels())
            .isEqualTo(service.findRelevantContext("hướng dẫn sạc pin an toàn").sourceLabels());
        assertThat(service.findRelevantContext("hướng dẫn sạc pin an toàn").hasSnippets()).isTrue();
    }

    @Test
    void nullBlankStopWordOnlyAndNonsenseQueriesFindNothing() {
        PdfKnowledgeBaseService service = real(new PdfKnowledgeProperties());
        assertThat(service.findRelevantContext(null).hasSnippets()).isFalse();
        assertThat(service.findRelevantContext("   ").hasSnippets()).isFalse();
        assertThat(service.findRelevantContext("la cho va xe").hasSnippets()).isFalse();
        assertThat(service.findRelevantContext("zzzqqqxxx wwwvvvuuu").hasSnippets()).isFalse();
        assertThat(service.findRelevantContext("?!?! ,,, ...").hasSnippets()).isFalse();
    }

    @Test
    void veryLongQueryDoesNotBlowUp() {
        PdfKnowledgeBaseService service = real(new PdfKnowledgeProperties());
        service.findRelevantContext("bảo hành ".repeat(5000));
    }

    @Test
    void disabledKnowledgeBaseIsEmpty() {
        PdfKnowledgeProperties properties = new PdfKnowledgeProperties();
        properties.setEnabled(false);
        assertThat(real(properties).findRelevantContext("bảo hành").hasSnippets()).isFalse();
    }

    @Test
    void notInitialisedServiceAnswersEmptyInsteadOfFailing() {
        assertThat(new PdfKnowledgeBaseService(new PdfKnowledgeProperties()).findRelevantContext("bảo hành").hasSnippets()).isFalse();
    }

    @Test
    void maxResultsIsRespectedAndMinScoreFilters() {
        PdfKnowledgeProperties one = new PdfKnowledgeProperties();
        one.setMaxResults(1);
        assertThat(real(one).findRelevantContext("bảo hành").snippets()).hasSize(1);
        PdfKnowledgeProperties strict = new PdfKnowledgeProperties();
        strict.setMinScore(10_000);
        assertThat(real(strict).findRelevantContext("bảo hành").hasSnippets()).isFalse();
    }

    @Test
    void resultsAreDeterministic() {
        PdfKnowledgeBaseService service = real(new PdfKnowledgeProperties());
        assertThat(service.findRelevantContext("thanh toán trả góp").snippets()).isEqualTo(service.findRelevantContext("thanh toán trả góp").snippets());
    }

    @Test
    void sourceLabelsNameTheFileWithoutExtensionAndThePage() {
        PdfKnowledgeContext context = real(new PdfKnowledgeProperties()).findRelevantContext("bảo hành");
        assertThat(context.sourceLabels()).allMatch(label -> !label.contains(".pdf") && label.matches(".* \\(trang \\d+\\)"));
        assertThat(PdfKnowledgeContext.empty().sourceLabels()).isEmpty();
        assertThat(PdfKnowledgeContext.empty().hasSnippets()).isFalse();
    }

    // ---- chunking, with a PDF made on the fly

    private Path pdf(Path dir, String name, String... pages) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 6);
                    stream.newLineAtOffset(5, 780);
                    // one line per 100 characters so that nothing runs off the page
                    String remaining = text;
                    while (!remaining.isEmpty()) {
                        int cut = Math.min(100, remaining.length());
                        stream.showText(remaining.substring(0, cut));
                        stream.newLineAtOffset(0, -7);
                        remaining = remaining.substring(cut);
                    }
                    stream.endText();
                }
            }
            Path file = dir.resolve(name);
            document.save(file.toFile());
            return file;
        }
    }

    private PdfKnowledgeBaseService over(Path dir, int chunk, int overlap) {
        PdfKnowledgeProperties properties = new PdfKnowledgeProperties();
        properties.setLocationPattern(dir.toUri() + "*.pdf");
        properties.setChunkSize(chunk);
        properties.setChunkOverlap(overlap);
        properties.setMinScore(1);
        properties.setMaxResults(50);
        return real(properties);
    }

    @Test
    void longPagesAreSplitIntoChunksNoLongerThanTheChunkSizeAndEveryWordIsFindable(@TempDir Path dir) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            text.append("alpha").append(i).append("zz filler words for padding ");
        }
        pdf(dir, "long-doc.pdf", text.toString());
        PdfKnowledgeBaseService service = over(dir, 300, 50);
        PdfKnowledgeContext first = service.findRelevantContext("alpha0zz");
        PdfKnowledgeContext last = service.findRelevantContext("alpha59zz");
        assertThat(first.hasSnippets()).isTrue();
        assertThat(last.hasSnippets()).isTrue();
        assertThat(first.snippets().get(0).excerpt().length()).isLessThanOrEqualTo(300);
        assertThat(first.snippets().get(0).excerpt()).isNotEqualTo(last.snippets().get(0).excerpt());
        assertThat(last.snippets().get(0).sourceName()).isEqualTo("long doc");
        assertThat(last.snippets().get(0).pageNumber()).isEqualTo(1);
    }

    @Test
    void chunkSizeBelowTheMinimumIsRaisedToTwoHundredFifty(@TempDir Path dir) throws Exception {
        pdf(dir, "tiny.pdf", "uniqueword ".repeat(60));
        PdfKnowledgeBaseService service = over(dir, 10, 0);
        assertThat(service.findRelevantContext("uniqueword").snippets()).isNotEmpty()
            .allMatch(s -> s.excerpt().length() <= 250);
    }

    @Test
    void pageNumbersAreKeptAndBlankOrShortPagesProduceNoChunk(@TempDir Path dir) throws Exception {
        pdf(dir, "pages.pdf", "short", "second page talks about quokkas and wombats in great detail so that the chunk is long enough to keep");
        PdfKnowledgeBaseService service = over(dir, 900, 100);
        PdfKnowledgeContext found = service.findRelevantContext("quokkas wombats");
        assertThat(found.snippets()).hasSize(1);
        assertThat(found.snippets().get(0).pageNumber()).isEqualTo(2);
        assertThat(service.findRelevantContext("short").hasSnippets()).isFalse();
    }

    @Test
    void anEmptyFolderOrACorruptPdfIsSurvived(@TempDir Path dir) throws Exception {
        assertThat(over(dir, 900, 100).findRelevantContext("anything at all").hasSnippets()).isFalse();
        Files.writeString(dir.resolve("broken.pdf"), "this is not a pdf");
        assertThat(over(dir, 900, 100).findRelevantContext("anything at all").hasSnippets()).isFalse();
    }

    @Test
    void exactPhraseOutranksScatteredWords(@TempDir Path dir) throws Exception {
        pdf(dir, "a-scattered.pdf", "kangaroo travels far while the wallaby sleeps and later the kangaroo returns to the wallaby home together");
        pdf(dir, "b-phrase.pdf", "the kangaroo wallaby is a very rare animal that lives in the hills and eats grass and drinks water daily");
        PdfKnowledgeBaseService service = over(dir, 900, 100);
        List<PdfKnowledgeBaseService.KnowledgeSnippet> snippets = service.findRelevantContext("kangaroo wallaby").snippets();
        assertThat(snippets).hasSize(2);
        assertThat(snippets.get(0).sourceName()).isEqualTo("b phrase");
    }
}
