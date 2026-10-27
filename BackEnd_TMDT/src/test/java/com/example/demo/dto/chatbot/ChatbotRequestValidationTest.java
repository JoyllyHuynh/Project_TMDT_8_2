package com.example.demo.dto.chatbot;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatbotRequestValidationTest {

    @Test
    void limitsMessageLengthAndHistoryCount() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            ChatbotRequest request = new ChatbotRequest(
                    "x".repeat(2001),
                    List.of(
                            new ChatbotHistoryMessage(ChatbotRole.USER, "one"),
                            new ChatbotHistoryMessage(ChatbotRole.USER, "two"),
                            new ChatbotHistoryMessage(ChatbotRole.USER, "three"),
                            new ChatbotHistoryMessage(ChatbotRole.USER, "four"),
                            new ChatbotHistoryMessage(ChatbotRole.USER, "five"),
                            new ChatbotHistoryMessage(ChatbotRole.USER, "six"),
                            new ChatbotHistoryMessage(ChatbotRole.USER, "seven"),
                            new ChatbotHistoryMessage(ChatbotRole.USER, "eight"),
                            new ChatbotHistoryMessage(ChatbotRole.USER, "nine")
                    )
            );

            assertFalse(validator.validate(request).isEmpty());
        }
    }
}
