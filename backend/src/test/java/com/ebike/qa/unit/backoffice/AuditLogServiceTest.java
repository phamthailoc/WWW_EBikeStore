package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ebike.model.entity.admin.AdminAuditLog;
import com.ebike.repository.admin.AdminAuditLogRepository;
import com.ebike.service.admin.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditLogServiceTest {

    AdminAuditLogRepository repo = mock(AdminAuditLogRepository.class);
    AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repo);
    }

    @AfterEach
    void tearDown() {
        Fx.signOut();
        RequestContextHolder.resetRequestAttributes();
    }

    private AdminAuditLog saved() {
        ArgumentCaptor<AdminAuditLog> c = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(repo).save(c.capture());
        return c.getValue();
    }

    @Test
    void record_usesSignedInActor_andRequestAddress() {
        Fx.signIn("boss");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        service.record("cập nhật quyền", "tien");
        AdminAuditLog log = saved();
        assertThat(log.getActor()).isEqualTo("boss");
        assertThat(log.getAction()).isEqualTo("cập nhật quyền");
        assertThat(log.getTarget()).isEqualTo("tien");
        assertThat(log.getIpAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void record_withoutAuthenticationOrRequest_isSystem() {
        service.record("job", "x");
        AdminAuditLog log = saved();
        assertThat(log.getActor()).isEqualTo("System");
        assertThat(log.getIpAddress()).isEqualTo("system");
    }

    @Test
    void record_blankPrincipalName_isSystem() {
        Fx.signIn("  ");
        service.record("a", "b");
        assertThat(saved().getActor()).isEqualTo("System");
    }

    @Test
    void recordBy_usesNamedActor_notTheSecurityContext() {
        Fx.signIn("someoneElse");
        service.recordBy("boss", "đăng nhập thất bại", "sai mật khẩu");
        assertThat(saved().getActor()).isEqualTo("boss");
    }

    @Test
    void longValuesAreTruncatedToColumnSizes_notRejected() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("2001:0db8:85a3:0000:0000:8a2e:0370:7334:extra-extra-extra-extra");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        service.recordBy("a".repeat(300), "b".repeat(300), "c".repeat(500));
        AdminAuditLog log = saved();
        assertThat(log.getActor()).hasSize(150);
        assertThat(log.getAction()).hasSize(150);
        assertThat(log.getTarget()).hasSize(255);
        assertThat(log.getIpAddress()).hasSizeLessThanOrEqualTo(45);
    }

    @Test
    void nullActionOrTarget_becomeEmptyStrings() {
        service.record(null, null);
        AdminAuditLog log = saved();
        assertThat(log.getAction()).isEmpty();
        assertThat(log.getTarget()).isEmpty();
    }

    @Test
    void exactlyAtTheLimit_isKeptWhole() {
        service.recordBy("a".repeat(150), "x", "t".repeat(255));
        AdminAuditLog log = saved();
        assertThat(log.getActor()).hasSize(150);
        assertThat(log.getTarget()).hasSize(255);
        verify(repo).save(any(AdminAuditLog.class));
    }
}
