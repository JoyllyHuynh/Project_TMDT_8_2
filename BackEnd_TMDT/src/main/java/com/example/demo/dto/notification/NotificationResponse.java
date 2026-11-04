package com.example.demo.dto.notification;

import com.example.demo.model.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String content,
        String orderCode,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getOrder().getOrderCode(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
