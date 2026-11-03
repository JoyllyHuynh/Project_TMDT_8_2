package com.example.demo.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Điều kiện bật notifier gửi mail thật: phải bật email-enabled và có đủ
 * host / username / password của SMTP. Nếu thiếu, hệ thống dùng notifier
 * dành cho môi trường phát triển (xem {@link MailNotConfiguredCondition}).
 */
public class ConditionalOnMailConfigured implements Condition {

    private static final String ENABLED_PROPERTY = "app.auth.password-reset.email-enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!"true".equalsIgnoreCase(context.getEnvironment().getProperty(ENABLED_PROPERTY))) {
            return false;
        }

        String host = context.getEnvironment().getProperty("spring.mail.host");
        String username = context.getEnvironment().getProperty("spring.mail.username");
        String password = context.getEnvironment().getProperty("spring.mail.password");

        return notBlank(host) && notBlank(username) && notBlank(password);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
