package com.example.demo.service;

import com.example.demo.dto.chatbot.ChatbotRequest;
import com.example.demo.dto.chatbot.ChatbotToolResult;
import com.example.demo.service.ChatbotAiProvider.ChatCompletion;
import com.example.demo.service.ChatbotAiProvider.ToolCall;
import com.example.demo.service.FaqKnowledgeService.FaqEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotServiceTest {

    private final ChatbotAiProvider aiProvider = mock(ChatbotAiProvider.class);
    private final ChatbotToolRegistry toolRegistry = mock(ChatbotToolRegistry.class);
    private final FaqKnowledgeService faqKnowledgeService = mock(FaqKnowledgeService.class);
    private final ChatbotService chatbotService = new ChatbotService(aiProvider, toolRegistry, faqKnowledgeService);

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void returnsGroundedFaqWhenProviderIsNotConfigured() {
        when(aiProvider.isConfigured()).thenReturn(false);
        when(faqKnowledgeService.search("hoàn tiền"))
                .thenReturn(List.of(new FaqEntry(
                        "Vận chuyển, đổi trả và hoàn tiền",
                        List.of("hoàn tiền"),
                        "Chính sách này chưa được xác minh.",
                        "/vu/request_support/request_support.html"
                )));

        var response = chatbotService.respond(new ChatbotRequest("hoàn tiền", List.of()));

        assertEquals("unconfigured", response.mode());
        assertEquals("Chính sách này chưa được xác minh.", response.answer());
        assertEquals("/vu/request_support/request_support.html", response.links().get(0).url());
        verify(aiProvider, never()).complete(anyList(), anyList());
    }

    @Test
    void doesNotClaimOrderLookupWorksWhenAiProviderIsUnconfigured() {
        when(aiProvider.isConfigured()).thenReturn(false);
        when(faqKnowledgeService.search("đơn hàng"))
                .thenReturn(List.of(new FaqEntry(
                        "Tra cứu đơn hàng của tôi",
                        List.of("đơn hàng"),
                        "Đăng nhập để tra cứu.",
                        "/vu/auth/login/login.html"
                )));

        var response = chatbotService.respond(new ChatbotRequest("đơn hàng", List.of()));

        assertTrue(response.requiresLogin());
        assertTrue(response.answer().contains("chưa được cấu hình"));
        assertEquals("/vu/auth/login/login.html", response.links().get(0).url());
    }

    @Test
    void executesAllowlistedToolThenReturnsProviderAnswer() {
        when(aiProvider.isConfigured()).thenReturn(true);
        when(toolRegistry.definitions()).thenReturn(List.of(Map.of("type", "function")));
        when(aiProvider.complete(anyList(), anyList()))
                .thenReturn(new ChatCompletion(
                        Map.of("role", "assistant", "tool_calls", List.of()),
                        "",
                        List.of(new ToolCall("call-1", "lookup_my_orders", "{}"))
                ))
                .thenReturn(new ChatCompletion(
                        Map.of("role", "assistant", "content", "Đây là thông tin đơn của bạn."),
                        "Đây là thông tin đơn của bạn.",
                        List.of()
                ));
        when(toolRegistry.execute("lookup_my_orders", "{}"))
                .thenReturn(new ChatbotToolResult("[]", List.of(), List.of(), true));

        var response = chatbotService.respond(new ChatbotRequest("Đơn hàng của tôi thế nào?", List.of()));

        assertEquals("ai", response.mode());
        assertTrue(response.requiresLogin());
        assertEquals("Đây là thông tin đơn của bạn.", response.answer());
        verify(toolRegistry).execute("lookup_my_orders", "{}");
    }

    @Test
    void usesProviderFallbackWhenProviderFails() {
        when(aiProvider.isConfigured()).thenReturn(true);
        when(toolRegistry.definitions()).thenReturn(List.of());
        when(aiProvider.complete(anyList(), anyList()))
                .thenThrow(new ChatbotProviderException("provider error"));
        when(faqKnowledgeService.search("hello")).thenReturn(List.of());

        var response = chatbotService.respond(new ChatbotRequest("hello", List.of()));

        assertEquals("unavailable", response.mode());
        assertTrue(response.answer().contains("tạm thời không khả dụng"));
        assertFalse(response.requiresLogin());
    }
}
