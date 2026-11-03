package com.example.demo.dto.chatbot;

import java.util.List;

public record ChatbotToolResult(
        String content,
        List<ChatbotProductCard> products,
        List<ChatbotLink> links,
        boolean requiresLogin
) {
    public static ChatbotToolResult text(String content) {
        return new ChatbotToolResult(content, List.of(), List.of(), false);
    }
}
