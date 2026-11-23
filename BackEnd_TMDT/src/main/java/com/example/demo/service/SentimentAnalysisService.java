package com.example.demo.service;

import com.example.demo.dto.sentiment.SentimentRequest;
import com.example.demo.dto.sentiment.SentimentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SentimentAnalysisService {

    private final RestClient.Builder restClientBuilder;

    @Value("${sentiment.api.url:http://127.0.0.1:8000}")
    private String sentimentApiUrl;

    public SentimentResponse analyze(String content) {

        try {
            RestClient restClient = restClientBuilder
                    .baseUrl(sentimentApiUrl)
                    .build();

            SentimentResponse response = restClient.post()
                    .uri("/predict")
                    .body(new SentimentRequest(content))
                    .retrieve()
                    .body(SentimentResponse.class);

            if (response == null || response.getSentiment() == null
                    || response.getConfidence() == null) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI không trả về kết quả phân tích hợp lệ."
                );
            }

            return response;

        } catch (RestClientException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối đến dịch vụ phân tích cảm xúc. Vui lòng thử lại sau."
            );
        }
    }
}