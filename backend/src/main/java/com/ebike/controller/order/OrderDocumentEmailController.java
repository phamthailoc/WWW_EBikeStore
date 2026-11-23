package com.ebike.controller.order;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.common.i18n.RequestLanguage;
import com.ebike.service.document.DeliveryResendService;
import com.ebike.service.document.DocumentText;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** V27: staff send the hand-over email (sales receipt and warranty card) of a delivered order again. */
@RestController
@RequestMapping("/api/v1/orders/{id}/documents")
public class OrderDocumentEmailController {

    private final DeliveryResendService resender;

    public OrderDocumentEmailController(DeliveryResendService resender) {
        this.resender = resender;
    }

    @PostMapping("/email")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.ORDER_UPDATE_STATUS + "')")
    public Map<String, String> resend(@PathVariable Long id) {
        String language = RequestLanguage.current();
        String email = resender.resend(id, language);
        return Map.of("email", email, "message", DocumentText.text(language, "resend.done", email));
    }
}
