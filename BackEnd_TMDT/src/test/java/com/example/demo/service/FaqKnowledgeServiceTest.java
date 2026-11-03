package com.example.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FaqKnowledgeServiceTest {

    @Test
    void unknownRefundPolicyDirectsUsersToSupportWithoutInventingTerms() {
        FaqKnowledgeService service = new FaqKnowledgeService(new ObjectMapper());

        var result = service.search("điều kiện hoàn tiền và đổi trả");

        assertTrue(result.stream().anyMatch(entry -> entry.answer().contains("chưa có nguồn chính thức")));
        assertTrue(result.stream().anyMatch(entry -> entry.url().equals("/vu/request_support/request_support.html")));
    }
}
