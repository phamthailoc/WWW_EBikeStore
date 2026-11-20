package com.ebike.service.warranty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** V20: the maintenance reminder email. A missing SMTP setup or a send failure is logged and never stops the reminder job. */
@Service
public class MaintenanceEmailSender {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final boolean mailEnabled;

    public MaintenanceEmailSender(
        JavaMailSender mailSender,
        @Value("${app.order.email-otp.from:no-reply@ebike.local}") String fromAddress,
        @Value("${spring.mail.host:}") String smtpHost,
        @Value("${app.order.email-otp.mail-enabled:true}") boolean mailEnabled
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
        this.mailEnabled = mailEnabled;
    }

    public void send(String email, String productName, String frameNumber, String milestone) {
        if (!mailEnabled || smtpHost == null || smtpHost.isBlank() || email == null || email.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Kinetic E-Bike: xe " + productName + " đến hạn bảo dưỡng");
        message.setText("""
            Xe %s (số khung %s) đã đến mốc bảo dưỡng định kỳ, %s kể từ ngày giao.

            Vui lòng mang xe đến cửa hàng Kinetic để được kiểm tra. Xem sổ bảo hành của bạn tại trang Bảo hành trong tài khoản. Bạn nhận email này vì đã mua xe tại Kinetic và đang bật nhận thông báo.
            """.formatted(productName, frameNumber, MaintenanceMilestones.describe(milestone)));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Could not send maintenance email to {}: {}", email, exception.getMessage());
        }
    }
}
