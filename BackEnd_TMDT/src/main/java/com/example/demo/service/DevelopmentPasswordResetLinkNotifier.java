package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DevelopmentPasswordResetLinkNotifier implements PasswordResetLinkNotifier {

    private final boolean logResetLink;

    public DevelopmentPasswordResetLinkNotifier(
            @Value("${app.auth.password-reset.log-link:false}") boolean logResetLink
    ) {
        this.logResetLink = logResetLink;
    }

    @Override
    public void sendResetLink(Long userId, String email, String resetUrl) {
        if (logResetLink) {
            log.warn("Development password-reset URL for user {} ({}): {}", userId, email, resetUrl);
        } else {
            log.warn("Password-reset email delivery is not configured; no reset link was delivered.");
        }
    }
}
