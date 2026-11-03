package com.example.demo.dto.review;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ProductReviewsResponse {

    private Double averageRating;
    private Long totalReviews;
    private Map<Integer, Long> distribution;
    private List<ReviewResponse> reviews;
}
