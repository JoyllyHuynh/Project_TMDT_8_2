package com.example.demo.dto.chatbot;

import java.util.List;

public record ChatbotResponse(
        String answer,
        String mode,
        boolean requiresLogin,
        List<ChatbotProductCard> products,
        List<ChatbotLink> links
) {
}
