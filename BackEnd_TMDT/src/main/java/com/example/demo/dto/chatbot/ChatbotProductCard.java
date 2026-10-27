package com.example.demo.dto.chatbot;

import java.math.BigDecimal;

public record ChatbotProductCard(
        Long id,
        String title,
        BigDecimal price,
        Integer stockQuantity,
        Integer conditionPercent,
        String imageUrl,
        String url
) {
}
