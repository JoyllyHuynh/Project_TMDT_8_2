package com.example.demo.dto.admin;

import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.OrderStatusHistory;
import com.example.demo.model.Refund;
import com.example.demo.model.enums.DisputeFavor;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.RefundStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Dữ liệu đơn hàng hiển thị trên Admin Console (Quản lý đơn hàng toàn sàn),
 * bao gồm thông tin tranh chấp nếu có.
 */
@Getter
@Builder
public class AdminOrderResponse {

    private Long id;
    private String orderCode;
    private Long buyerId;
    private String buyerName;
    private Long sellerId;
    private String sellerName;
    private BigDecimal subtotalAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String recipientName;
    private String recipientPhone;
    private String shippingAddress;
    private OrderStatus status;
    private OrderPaymentStatus paymentStatus;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    private String disputeReason;
    private LocalDateTime disputedAt;
    private DisputeFavor disputeFavor;
    private String disputeNote;
    private LocalDateTime disputeResolvedAt;

    private List<Item> items;

    /** Lịch sử đổi trạng thái (chỉ trả về ở endpoint chi tiết đơn). */
    private List<CancelHistory> cancelHistory;

    /** Thông tin hoàn tiền (chỉ có khi đơn đã được hoàn tiền). */
    private RefundResponse refund;

    public static AdminOrderResponse from(Order order, List<OrderItem> orderItems) {
        return from(order, orderItems, null, null);
    }

    public static AdminOrderResponse from(
            Order order,
            List<OrderItem> orderItems,
            List<OrderStatusHistory> history,
            Refund refund
    ) {
        return AdminOrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .buyerId(order.getBuyer() != null ? order.getBuyer().getId() : null)
                .buyerName(order.getBuyer() != null ? order.getBuyer().getFullName() : null)
                .sellerId(order.getSeller() != null ? order.getSeller().getId() : null)
                .sellerName(order.getSeller() != null ? order.getSeller().getFullName() : null)
                .subtotalAmount(order.getSubtotalAmount())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .shippingAddress(order.getShippingAddress())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .cancelReason(order.getCancelReason())
                .cancelledAt(order.getCancelledAt())
                .createdAt(order.getCreatedAt())
                .completedAt(order.getCompletedAt())
                .disputeReason(order.getDisputeReason())
                .disputedAt(order.getDisputedAt())
                .disputeFavor(order.getDisputeFavor())
                .disputeNote(order.getDisputeNote())
                .disputeResolvedAt(order.getDisputeResolvedAt())
                .items(orderItems == null ? List.of() : orderItems.stream().map(Item::from).toList())
                .cancelHistory(history == null
                        ? List.of()
                        : history.stream().map(CancelHistory::from).toList())
                .refund(refund == null ? null : RefundResponse.from(refund))
                .build();
    }

    @Getter
    @Builder
    public static class CancelHistory {
        private Long id;
        private OrderStatus previousStatus;
        private OrderStatus newStatus;
        private String note;
        private String changedBy;
        private LocalDateTime changedAt;

        public static CancelHistory from(OrderStatusHistory h) {
            return CancelHistory.builder()
                    .id(h.getId())
                    .previousStatus(h.getPreviousStatus())
                    .newStatus(h.getNewStatus())
                    .note(h.getNote())
                    .changedBy(h.getChangedBy() != null ? h.getChangedBy().getFullName() : "")
                    .changedAt(h.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class Item {
        private Long id;
        private Long productId;
        private String productTitle;
        private String imageUrl;
        private Integer conditionPercent;
        private BigDecimal unitPrice;
        private Integer quantity;

        public static Item from(OrderItem item) {
            return Item.builder()
                    .id(item.getId())
                    .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                    .productTitle(item.getProductTitle())
                    .imageUrl(item.getImageUrl())
                    .conditionPercent(item.getConditionPercent())
                    .unitPrice(item.getUnitPrice())
                    .quantity(item.getQuantity())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class RefundResponse {
        private String refundCode;
        private String idempotencyKey;
        private RefundStatus status;
        private BigDecimal amount;
        private LocalDateTime completedAt;

        public static RefundResponse from(Refund refund) {
            return RefundResponse.builder()
                    .refundCode(refund.getRefundCode())
                    .idempotencyKey(refund.getIdempotencyKey())
                    .status(refund.getStatus())
                    .amount(refund.getAmount())
                    .completedAt(refund.getCompletedAt())
                    .build();
        }
    }
}
