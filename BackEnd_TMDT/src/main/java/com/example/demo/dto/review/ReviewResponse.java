package com.example.demo.dto.review;

import com.example.demo.model.Review;
import com.example.demo.model.ReviewReply;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ReviewResponse {

    private Long id;
    private Long orderItemId;
    private String orderCode;
    private Long productId;
    private String productTitle;
    private String imageUrl;
    private Integer conditionPercent;
    private Long buyerId;
    private String buyerName;
    private Integer rating;
    private String content;
    private List<String> images;
    private LocalDateTime createdAt;
    private ReplyResponse reply;

    public static ReviewResponse from(Review review, ReviewReply reply, List<String> images) {
        return ReviewResponse.builder()
                .id(review.getId())
                .orderItemId(review.getOrderItem().getId())
                .orderCode(review.getOrderItem().getOrder().getOrderCode())
                .productId(review.getOrderItem().getProduct().getId())
                .productTitle(review.getOrderItem().getProductTitle())
                .imageUrl(review.getOrderItem().getImageUrl())
                .conditionPercent(review.getOrderItem().getConditionPercent())
                .buyerId(review.getBuyer().getId())
                .buyerName(review.getBuyer().getFullName())
                .rating(review.getRating())
                .content(review.getContent())
                .images(images == null ? List.of() : images)
                .createdAt(review.getCreatedAt())
                .reply(reply == null ? null : ReplyResponse.from(reply))
                .build();
    }

    @Getter
    @Builder
    public static class ReplyResponse {

        private Long id;
        private String sellerName;
        private String content;
        private LocalDateTime createdAt;

        public static ReplyResponse from(ReviewReply reply) {
            return ReplyResponse.builder()
                    .id(reply.getId())
                    .sellerName(reply.getSeller().getFullName())
                    .content(reply.getContent())
                    .createdAt(reply.getCreatedAt())
                    .build();
        }
    }
}
