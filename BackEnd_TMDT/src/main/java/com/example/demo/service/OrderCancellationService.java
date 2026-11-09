package com.example.demo.service;

import com.example.demo.dto.order.OrderResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.*;
import com.example.demo.model.enums.CheckoutBatchStatus;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.RefundStatus;
import com.example.demo.repository.*;
import com.example.demo.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private static final String BUYER_CANCEL_REASON = "Người mua hủy đơn hàng.";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final CheckoutBatchRepository checkoutBatchRepository;
    private final VoucherService voucherService;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final RefundRepository refundRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order orderSnapshot = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng."));

        CheckoutBatch checkout = checkoutBatchRepository
                .findByIdForUpdate(orderSnapshot.getCheckout().getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiên thanh toán của đơn hàng."));

        Order order = orderRepository.findByCheckoutIdForUpdate(checkout.getId())
                .filter(lockedOrder -> lockedOrder.getId().equals(orderId))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng."));

        Long buyerId = SecurityUtils.currentUserId();
        if (!order.getBuyer().getId().equals(buyerId)) {
            throw ApiException.forbidden("Bạn không có quyền hủy đơn hàng này.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            List<OrderItem> items = orderItemRepository.findByOrder(order);

            String paymentCode = paymentRepository
                    .findFirstByCheckoutOrderByCreatedAtDesc(order.getCheckout())
                    .map(Payment::getPaymentCode)
                    .orElse(null);

            return OrderResponse.from(order, items, paymentCode);
        }

        if (!isCancellable(order.getStatus())) {
            throw ApiException.badRequest(
                    "Chỉ có thể hủy đơn hàng trước khi người bán bàn giao cho đơn vị vận chuyển."
            );
        }

        List<OrderItem> items = orderItemRepository.findByOrder(order);
        if (items.isEmpty()) {
            throw ApiException.badRequest("Đơn hàng không có sản phẩm để hoàn lại tồn kho.");
        }

        LocalDateTime cancelledAt = LocalDateTime.now();
        User buyer = order.getBuyer();
        OrderStatus previousStatus = order.getStatus();

        releaseStock(order, items, cancelledAt);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledBy(buyer);
        order.setCancelReason(BUYER_CANCEL_REASON);
        order.setCancelledAt(cancelledAt);

        voucherService.releaseVouchersForOrder(order);
        if (checkout.getStatus() == CheckoutBatchStatus.PENDING) {
            checkout.setStatus(CheckoutBatchStatus.CANCELLED);
            checkout.setCompletedAt(cancelledAt);
        }

        if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
            createRefundIfNeeded(order, cancelledAt);
        }

        recordStatusHistory(order, previousStatus, buyer);
        createCancellationNotifications(order, buyer);
        orderRepository.save(order);

        String paymentCode = paymentRepository
                .findFirstByCheckoutOrderByCreatedAtDesc(order.getCheckout())
                .map(Payment::getPaymentCode)
                .orElse(null);

        return OrderResponse.from(order, items, paymentCode);
    }

    private boolean isCancellable(OrderStatus status) {
        return status == OrderStatus.WAITING_PAYMENT
                || status == OrderStatus.WAITING_CONFIRM
                || status == OrderStatus.PACKING;
    }

    private void releaseStock(Order order, List<OrderItem> items, LocalDateTime releasedAt) {
        if (order.getStockReleasedAt() != null) {
            return;
        }

        for (OrderItem item : items) {
            if (item.getProduct() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw ApiException.badRequest("Chi tiết sản phẩm trong đơn hàng không hợp lệ.");
            }

            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> ApiException.notFound(
                            "Không tìm thấy sản phẩm cần hoàn lại tồn kho."
                    ));
            int currentStock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
            if (currentStock > Integer.MAX_VALUE - item.getQuantity()) {
                throw ApiException.badRequest("Số lượng tồn kho vượt giới hạn cho phép.");
            }
            product.setStockQuantity(currentStock + item.getQuantity());
        }

        productRepository.flush();
        order.setStockReleasedAt(releasedAt);
    }

    private void createRefundIfNeeded(Order order, LocalDateTime cancelledAt) {
        if (refundRepository.findFirstByOrderOrderByCreatedAtDesc(order).isPresent()) {
            order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
            return;
        }

        List<PaymentAllocation> allocations = paymentAllocationRepository.findByOrder(order);
        if (allocations.isEmpty()) {
            order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
            return;
        }

        BigDecimal amount = allocations.stream()
                .map(PaymentAllocation::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.signum() <= 0 && order.getTotalAmount() != null) {
            amount = order.getTotalAmount();
        }

        Refund refund = new Refund();
        refund.setOrder(order);
        refund.setPayment(allocations.get(0).getPayment());
        refund.setRefundCode("RF-" + order.getOrderCode());
        refund.setIdempotencyKey("BUYER-CANCEL-" + order.getId());
        refund.setAmount(amount);
        refund.setStatus(RefundStatus.SUCCEEDED);
        refund.setCompletedAt(cancelledAt);
        refundRepository.save(refund);
        order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
    }

    private void recordStatusHistory(Order order, OrderStatus previousStatus, User buyer) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(OrderStatus.CANCELLED);
        history.setChangedBy(buyer);
        history.setNote(BUYER_CANCEL_REASON);
        orderStatusHistoryRepository.save(history);
    }

    private void createCancellationNotifications(Order order, User buyer) {
        createNotification(
                buyer,
                order,
                "buyer",
                "Đã hủy đơn hàng",
                "Đơn hàng " + order.getOrderCode()
                        + " đã được hủy. Số lượng sản phẩm đã được hoàn lại kho."
        );
        createNotification(
                order.getSeller(),
                order,
                "seller",
                "Người mua đã hủy đơn hàng",
                "Người mua đã hủy đơn hàng " + order.getOrderCode()
                        + " trước khi đơn được bàn giao cho đơn vị vận chuyển."
        );
    }

    private void createNotification(
            User recipient,
            Order order,
            String recipientRole,
            String title,
            String content
    ) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setOrder(order);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setDeduplicationKey("ORDER-CANCEL-" + order.getId() + "-" + recipientRole);
        notificationRepository.save(notification);
    }
}
