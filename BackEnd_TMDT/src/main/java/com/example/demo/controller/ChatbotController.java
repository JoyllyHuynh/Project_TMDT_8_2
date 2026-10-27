package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.chatbot.ChatbotRequest;
import com.example.demo.dto.chatbot.ChatbotResponse;
import com.example.demo.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/message")
    public ResponseEntity<ApiResponse<ChatbotResponse>> sendMessage(
            @Valid @RequestBody ChatbotRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(chatbotService.respond(request)));
    }
}
