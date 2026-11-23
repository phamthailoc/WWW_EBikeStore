package com.ebike.service.document;

import com.ebike.service.admin.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * V27: staff send the hand-over email of a delivered order again. The first email is not retried when the mail server was down or the queue was full,
 * and a guest cannot download the PDFs, so this is the only way such a customer gets them. It runs in the request (not on the pool), so the staff member
 * sees at once whether the mail server took the message. The caller is rate limited and needs the permission to update an order.
 */
@Service
public class DeliveryResendService {

    private final OrderDocumentService documents;
    private final DeliveryEmailSender sender;
    private final AuditLogService auditLog;

    public DeliveryResendService(OrderDocumentService documents, DeliveryEmailSender sender, AuditLogService auditLog) {
        this.documents = documents;
        this.sender = sender;
        this.auditLog = auditLog;
    }

    /** Sends the receipt (and the warranty cards) again and returns the address it went to. Errors are worded in {@code pageLanguage}. */
    public String resend(Long orderId, String pageLanguage) {
        if (!sender.canSend()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DocumentText.text(pageLanguage, "error.resendMailOff"));
        }
        OrderDocumentService.DeliveryDocuments found = documents.forDeliveredOrder(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, DocumentText.text(pageLanguage, "error.resendNothing")));
        if (!sender.send(found)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, DocumentText.text(pageLanguage, "error.resendFailed"));
        }
        auditLog.record("gửi lại email phiếu", found.orderNumber());
        return found.email();
    }
}
