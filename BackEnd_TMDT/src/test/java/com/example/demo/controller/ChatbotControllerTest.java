package com.example.demo.controller;

import com.example.demo.dto.chatbot.ChatbotResponse;
import com.example.demo.service.ChatbotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatbotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatbotService chatbotService;

    @Test
    void allowsAnonymousCatalogQuestionAndReturnsApiResponseContract() throws Exception {
        when(chatbotService.respond(any())).thenReturn(
                new ChatbotResponse("Có thể tìm trong catalog.", "ai", false, List.of(), List.of())
        );

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Tìm manga\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.answer").value("Có thể tìm trong catalog."))
                .andExpect(jsonPath("$.data.mode").value("ai"));

        verify(chatbotService).respond(any());
    }

    @Test
    void rejectsOversizedInputBeforeCallingService() throws Exception {
        String message = "x".repeat(2001);
        String body = "{\"message\":\"" + message + "\",\"history\":[]}";

        mockMvc.perform(post("/api/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false));

        verify(chatbotService, never()).respond(any());
    }
}
