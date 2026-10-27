package com.example.demo.dto.chatbot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ChatbotOrderSummary(
        String orderCode,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        List<Item> items
) {
    public record Item(String title, Integer quantity, BigDecimal unitPrice) {
    }
}
