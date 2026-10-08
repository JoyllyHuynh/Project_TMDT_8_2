package com.example.demo.dto.order;

import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderResponse {

    private Long id;
    private String orderCode;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal subtotalAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String recipientName;
    private String recipientPhone;
    private String shippingAddress;
    private String buyerNote;
    private OrderStatus status;
    private OrderPaymentStatus paymentStatus;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;

    public static OrderResponse from(Order order, List<OrderItem> items) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .buyerId(order.getBuyer().getId())
                .sellerId(order.getSeller().getId())
                .subtotalAmount(order.getSubtotalAmount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .shippingAddress(order.getShippingAddress())
                .buyerNote(order.getBuyerNote())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .createdAt(order.getCreatedAt())
                .items(items.stream().map(OrderItemResponse::from).toList())
                .build();
    }

    @Getter
    @Builder
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private String productTitle;
        private String imageUrl;
        private Integer conditionPercent;
        private BigDecimal unitPrice;
        private Integer quantity;

        public static OrderItemResponse from(OrderItem item) {
            return OrderItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productTitle(item.getProductTitle())
                    .imageUrl(item.getImageUrl())
                    .conditionPercent(item.getConditionPercent())
                    .unitPrice(item.getUnitPrice())
                    .quantity(item.getQuantity())
                    .build();
        }
    }
}
