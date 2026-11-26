package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ebike.service.auth.AccountEmailSender;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class AccountEmailSenderTest {

    JavaMailSender mail = mock(JavaMailSender.class);

    private AccountEmailSender sender(String host, boolean enabled, String baseUrl) {
        return new AccountEmailSender(mail, "no-reply@ebike.test", host, enabled, baseUrl);
    }

    @Test
    void canSend_requiresMailEnabledAndSmtpHost() {
        assertThat(sender("smtp.x", true, "").canSend()).isTrue();
        assertThat(sender("smtp.x", false, "").canSend()).isFalse();
        assertThat(sender("", true, "").canSend()).isFalse();
        assertThat(sender("  ", true, "").canSend()).isFalse();
        assertThat(sender(null, true, "").canSend()).isFalse();
    }

    @Test
    void sendWelcome_whenMailOff_returnsFalseAndSendsNothing() {
        assertThat(sender("", true, "").sendWelcome("a@b.com", "A", "a")).isFalse();
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendWelcome_withoutRecipient_returnsFalse() {
        assertThat(sender("smtp", true, "").sendWelcome(null, "A", "a")).isFalse();
        assertThat(sender("smtp", true, "").sendWelcome("  ", "A", "a")).isFalse();
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendWelcome_success_buildsMessage_andNeverContainsAPassword() {
        assertThat(sender("smtp", true, "https://shop.example/").sendWelcome("a@b.com", "Le Tien", "tien")).isTrue();
        ArgumentCaptor<SimpleMailMessage> msg = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(msg.capture());
        assertThat(msg.getValue().getTo()).containsExactly("a@b.com");
        assertThat(msg.getValue().getFrom()).isEqualTo("no-reply@ebike.test");
        assertThat(msg.getValue().getText()).contains("Xin chào Le Tien").contains("Tên đăng nhập: tien").contains("https://shop.example/login");
        assertThat(msg.getValue().getText()).doesNotContainIgnoringCase("mật khẩu:");
    }

    @Test
    void sendWelcome_smtpFailure_isSwallowed_returnsFalse() {
        doThrow(new MailSendException("boom")).when(mail).send(any(SimpleMailMessage.class));
        assertThat(sender("smtp", true, "").sendWelcome("a@b.com", "A", "a")).isFalse();
    }

    private static String body(String name, String username, String email, String url) throws Exception {
        Method m = AccountEmailSender.class.getDeclaredMethod("welcomeBody", String.class, String.class, String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, name, username, email, url);
    }

    private static String loginUrl(String base) throws Exception {
        Method m = AccountEmailSender.class.getDeclaredMethod("loginUrl", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, base);
    }

    @Test
    void welcomeBody_usesUsernameWhenDisplayNameBlank_andOmitsLinkWithoutBaseUrl() throws Exception {
        String text = body(" ", "tien", "a@b.com", null);
        assertThat(text).startsWith("Xin chào tien,").doesNotContain("/login").contains("Email: a@b.com");
    }

    @Test
    void loginUrl_normalisesTrailingSlashAndBlank() throws Exception {
        assertThat(loginUrl(null)).isNull();
        assertThat(loginUrl("   ")).isNull();
        assertThat(loginUrl("https://x.test")).isEqualTo("https://x.test/login");
        assertThat(loginUrl(" https://x.test/ ")).isEqualTo("https://x.test/login");
    }
}
