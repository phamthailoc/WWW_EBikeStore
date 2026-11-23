package com.ebike.controller.web;

import com.ebike.service.seo.SeoMarkup;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** V35: gives every page of the shop and the workspaces {@code seo} (not the REST API, which has no pages), the helper that builds its canonical address, Open Graph image and structured data. */
@ControllerAdvice(assignableTypes = {StorefrontController.class, WorkspaceController.class})
public class SeoModelAdvice {
    private final String publicBaseUrl;

    public SeoModelAdvice(@Value("${app.public-base-url:}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    @ModelAttribute("seo")
    public SeoMarkup seo(HttpServletRequest request) {
        return SeoController.markup(publicBaseUrl, request);
    }
}
