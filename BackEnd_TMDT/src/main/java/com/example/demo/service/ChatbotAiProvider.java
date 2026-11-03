package com.example.demo.service;

import java.util.List;
import java.util.Map;

public interface ChatbotAiProvider {

    boolean isConfigured();

    ChatCompletion complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools);

    record ChatCompletion(
            Map<String, Object> assistantMessage,
            String content,
            List<ToolCall> toolCalls
    ) {
    }

    record ToolCall(String id, String name, String arguments) {
    }
}
