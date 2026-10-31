package com.ebike.chatbotModule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.chatbotModule.config.PdfKnowledgeProperties;
import org.junit.jupiter.api.Test;

class PdfKnowledgeBaseServiceTest {

    @Test
    void findRelevantContextPrioritizesWarrantyDocument() {
        PdfKnowledgeProperties properties = new PdfKnowledgeProperties();
        properties.setLocationPattern("file:src/main/resources/docs/*.pdf");
        properties.setMaxResults(3);
        properties.setMinScore(2.0);

        PdfKnowledgeBaseService service = new PdfKnowledgeBaseService(properties);
        service.initialize();

        PdfKnowledgeBaseService.PdfKnowledgeContext context = service.findRelevantContext("toi muon biet ve chinh sach bao hanh");

        assertThat(context.hasSnippets()).isTrue();
        assertThat(context.snippets().get(0).sourceName()).contains("bao hanh");
        assertThat(context.combinedContext()).containsIgnoringCase("bao hanh");
        assertThat(context.combinedContext()).doesNotContain("Ã", "áº", "á»", "â");
    }
}
