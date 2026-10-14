package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.auth.password-reset.email-enabled", havingValue = "true")
public class SmtpPasswordResetLinkNotifier implements PasswordResetLinkNotifier {

    private final JavaMailSender mailSender;

    @Value("${app.auth.password-reset.from:no-reply@comicworm.local}")
    private String from;

    @Value("${app.auth.password-reset.reply-to:}")
    private String replyTo;

    @Override
    public void sendResetLink(Long userId, String email, String resetUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
            helper.setFrom(from);
            if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
            helper.setTo(email);
            helper.setSubject("Yêu cầu đặt lại mật khẩu - ComicWorm");

            String html = "<p>Xin chào,</p>"
                    + "<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản liên kết với email này.</p>"
                    + "<p>Nhấn vào liên kết bên dưới để đặt lại mật khẩu (liên kết có hiệu lực trong "
                    + "${app.auth.password-reset.expiration-minutes} phút):</p>"
                    + "<p><a href=\"" + resetUrl + "\">Đặt lại mật khẩu</a></p>"
                    + "<p>Nếu bạn không yêu cầu, có thể bỏ qua email này.</p>"
                    + "<p>— ComicWorm</p>";

            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent password reset email to {}", email);
        } catch (Exception ex) {
            log.error("Failed to send password reset email to {}: {}", email, ex.getMessage(), ex);
            // Fallback: still log resetUrl in server log so dev can continue testing
            log.warn("Reset URL (fallback): {}", resetUrl);
        }
    }
}