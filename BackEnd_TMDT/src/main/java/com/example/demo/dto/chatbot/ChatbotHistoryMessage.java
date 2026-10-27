package com.example.demo.dto.chatbot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatbotHistoryMessage(
        @NotNull ChatbotRole role,
        @NotBlank @Size(max = 1000) String content
) {
}
