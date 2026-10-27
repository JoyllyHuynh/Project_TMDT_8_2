package com.example.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FaqKnowledgeService {

    private final List<FaqEntry> entries;

    public FaqKnowledgeService(ObjectMapper objectMapper) {
        try (var input = new ClassPathResource("chatbot/knowledge-base.json").getInputStream()) {
            entries = List.of(objectMapper.readValue(input, FaqEntry[].class));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load chatbot knowledge base.", exception);
        }
    }

    public List<FaqEntry> search(String query) {
        String normalizedQuery = normalize(query);
        Set<String> queryWords = words(normalizedQuery);
        return entries.stream()
                .map(entry -> new ScoredEntry(entry, score(entry, normalizedQuery, queryWords)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredEntry::score).reversed())
                .limit(3)
                .map(ScoredEntry::entry)
                .toList();
    }

    private int score(FaqEntry entry, String query, Set<String> queryWords) {
        int score = 0;
        for (String keyword : entry.keywords()) {
            String normalizedKeyword = normalize(keyword);
            if (query.contains(normalizedKeyword)) {
                score += 3;
            } else {
                score += (int) words(normalizedKeyword).stream().filter(queryWords::contains).count();
            }
        }
        return score;
    }

    private Set<String> words(String value) {
        return Arrays.stream(value.split("\\s+"))
                .filter(word -> word.length() > 1)
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    public record FaqEntry(String question, List<String> keywords, String answer, String url) {
    }

    private record ScoredEntry(FaqEntry entry, int score) {
    }
}
