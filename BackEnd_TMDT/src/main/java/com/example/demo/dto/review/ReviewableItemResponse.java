package com.example.demo.dto.review;

import com.example.demo.model.OrderItem;
import com.example.demo.model.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewableItemResponse {

    private Long id;
    private Long orderId;
    private String orderCode;
    private Long productId;
    private String productTitle;
    private String imageUrl;
    private Integer conditionPercent;
    private BigDecimal unitPrice;
    private Integer quantity;
    private String sellerName;
    private OrderStatus orderStatus;
    private LocalDateTime deliveredAt;

    public static ReviewableItemResponse from(OrderItem item) {
        LocalDateTime deliveredAt = item.getOrder().getCompletedAt() != null
                ? item.getOrder().getCompletedAt()
                : item.getOrder().getUpdatedAt();
        return ReviewableItemResponse.builder()
                .id(item.getId())
                .orderId(item.getOrder().getId())
                .orderCode(item.getOrder().getOrderCode())
                .productId(item.getProduct().getId())
                .productTitle(item.getProductTitle())
                .imageUrl(item.getImageUrl())
                .conditionPercent(item.getConditionPercent())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .sellerName(item.getOrder().getSeller().getFullName())
                .orderStatus(item.getOrder().getStatus())
                .deliveredAt(deliveredAt)
                .build();
    }
}
