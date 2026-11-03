package com.example.demo.dto.chatbot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatbotRequest(
        @NotBlank @Size(max = 2000) String message,
        @Size(max = 8) List<@NotNull @Valid ChatbotHistoryMessage> history
) {
}
