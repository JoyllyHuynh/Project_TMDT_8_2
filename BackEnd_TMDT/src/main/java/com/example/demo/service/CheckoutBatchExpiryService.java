package com.example.demo.service;

import com.example.demo.model.CheckoutBatch;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Product;
import com.example.demo.model.enums.CheckoutBatchStatus;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.repository.CheckoutBatchRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckoutBatchExpiryService {

    private final CheckoutBatchRepository checkoutBatchRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final VoucherService voucherService;

    @Transactional
    public void expireCheckoutBatch(Long checkoutBatchId) {

        // 1. Khóa CheckoutBatch để tránh xử lý đồng thời.
        CheckoutBatch checkout = checkoutBatchRepository.findByIdForUpdate(
                checkoutBatchId
        ).orElse(null);

        if (checkout == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 2. Chỉ xử lý CheckoutBatch đang PENDING và đã hết hạn.
        if (checkout.getStatus() != CheckoutBatchStatus.PENDING
                || checkout.getExpiresAt().isAfter(now)) {
            return;
        }

        // 3. Lấy Order thuộc CheckoutBatch.
        Order order = orderRepository
                .findByCheckoutIdForUpdate(checkout.getId())
                .orElse(null);

        if (order == null) {
            checkout.setStatus(CheckoutBatchStatus.EXPIRED);
            checkout.setCompletedAt(now);
            return;
        }

        // 4. Không hủy Order đã thanh toán.
        if (order.getPaymentStatus() != OrderPaymentStatus.PENDING
                || order.getStatus() != OrderStatus.WAITING_PAYMENT) {
            return;
        }

        // 5. Hoàn lại tồn kho.
        releaseStock(order);

        // 6. Hoàn lại lượt sử dụng Voucher.
        voucherService.releaseVouchersForOrder(order);

        // 7. Hủy Order.
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setCancelReason("Checkout hết hạn sau 15 phút.");

        // 8. Đánh dấu CheckoutBatch hết hạn.
        checkout.setStatus(CheckoutBatchStatus.EXPIRED);
        checkout.setCompletedAt(now);

        orderRepository.save(order);
        checkoutBatchRepository.save(checkout);
    }

    private void releaseStock(Order order) {

        // Tránh hoàn kho nhiều lần.
        if (order.getStockReleasedAt() != null) {
            return;
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);

        for (OrderItem item : orderItems) {

            if (item.getProduct() == null || item.getQuantity() == null) {
                continue;
            }

            Product product = productRepository.findByIdForUpdate(
                    item.getProduct().getId()
            ).orElse(null);

            if (product == null) {
                continue;
            }

            int currentStock = product.getStockQuantity() == null
                    ? 0
                    : product.getStockQuantity();

            product.setStockQuantity(currentStock + item.getQuantity());

            productRepository.save(product);
        }

        order.setStockReleasedAt(LocalDateTime.now());
    }
}