package com.example.demo.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Điều kiện dùng notifier "chế độ phát triển": chỉ bật khi KHÔNG cấu hình
 * được SMTP thật, để ứng dụng luôn có đúng một {@code PasswordResetLinkNotifier}.
 */
public class MailNotConfiguredCondition implements Condition {

    private final ConditionalOnMailConfigured mailConfigured = new ConditionalOnMailConfigured();

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !mailConfigured.matches(context, metadata);
    }
}
