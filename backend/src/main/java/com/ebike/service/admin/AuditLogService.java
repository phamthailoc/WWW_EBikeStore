package com.ebike.service.admin;

import com.ebike.model.entity.admin.AdminAuditLog;
import com.ebike.repository.admin.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the audit trail shown on the Admin "Nhật ký" page: who did what, to what, and from which address.
 *
 * <p>The actor is the signed-in user and the address is the caller's, both taken from the current request, so
 * services only say what happened. Each entry is saved in its own transaction (the public methods are the transaction boundary): a refused sign-in attempt must stay
 * on record even though the request that made it is rolled back.
 */
@Service
public class AuditLogService {
    private final AdminAuditLogRepository logs;

    public AuditLogService(AdminAuditLogRepository logs) {
        this.logs = logs;
    }

    /** Records an action of the signed-in user. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String target) {
        save(currentActor(), action, target);
    }

    /** Records an action of a named user, for places where nobody is signed in yet (sign-in attempts). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBy(String actor, String action, String target) {
        save(actor, action, target);
    }

    private void save(String actor, String action, String target) {
        AdminAuditLog log = new AdminAuditLog();
        log.setActor(truncate(actor, 150));
        log.setAction(truncate(action, 150));
        log.setTarget(truncate(target, 255));
        log.setIpAddress(clientAddress());
        logs.save(log);
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "System";
        }
        return authentication.getName();
    }

    /** The caller's address; behind a proxy this is the real client when forward headers are enabled (prod does). */
    private static String clientAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String address = request.getRemoteAddr();
            if (address != null && !address.isBlank()) {
                return truncate(address, 45);
            }
        }
        return "system";
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
