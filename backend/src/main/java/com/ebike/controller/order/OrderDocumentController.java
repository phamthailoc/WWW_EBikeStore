package com.ebike.controller.order;

import com.ebike.common.i18n.RequestLanguage;
import com.ebike.service.document.DocumentFormat;
import com.ebike.service.document.OrderDocumentService;
import com.ebike.service.document.PdfFile;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V27: downloads of the sales receipt and the warranty card. The document is in the page language (cookie or account) unless {@code lang=vi|en} says otherwise;
 * that choice is only the language of the document, the texts of an error stay in the page language, and the files carry personal data so they are never cached.
 */
@RestController
@RequestMapping("/api/v1/orders/{id}")
public class OrderDocumentController {

    private final OrderDocumentService documents;

    public OrderDocumentController(OrderDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id, @RequestParam(required = false) String lang, Authentication authentication) {
        return pdf(documents.receipt(id, authentication, language(lang), RequestLanguage.current()));
    }

    @GetMapping("/warranty-card")
    public ResponseEntity<byte[]> warrantyCard(
        @PathVariable Long id,
        @RequestParam(required = false) Long unitId,
        @RequestParam(required = false) String lang,
        Authentication authentication
    ) {
        return pdf(documents.warrantyCard(id, unitId, authentication, language(lang), RequestLanguage.current()));
    }

    private static String language(String requested) {
        return requested == null || requested.isBlank() ? RequestLanguage.current() : DocumentFormat.language(requested);
    }

    private static ResponseEntity<byte[]> pdf(PdfFile file) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore())
            .contentLength(file.content().length)
            .body(file.content());
    }
}
