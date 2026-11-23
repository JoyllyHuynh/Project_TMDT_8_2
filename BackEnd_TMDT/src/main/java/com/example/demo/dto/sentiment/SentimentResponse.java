package com.example.demo.dto.sentiment;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SentimentResponse {

    private String text;
    private String sentiment;
    private Double confidence;
}