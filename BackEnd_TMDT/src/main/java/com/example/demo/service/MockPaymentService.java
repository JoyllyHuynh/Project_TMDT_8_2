
package com.example.demo.service;

import com.example.demo.dto.payment.MockPaymentRequest;
import com.example.demo.dto.payment.MockPaymentResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.CheckoutBatch;
import com.example.demo.model.Order;
import com.example.demo.model.Payment;
import com.example.demo.model.enums.CheckoutBatchStatus;
import com.example.demo.model.enums.GatewayPaymentStatus;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.repository.CheckoutBatchRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.util.CodeGenerator;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MockPaymentService {

    private final PaymentRepository paymentRepository;
    private final CheckoutBatchRepository checkoutBatchRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public MockPaymentResponse processPayment(
            String paymentCode,
            MockPaymentRequest request
    ) {
        // 1. Tìm Payment theo mã giao dịch
        Payment paymentSnapshot = paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> ApiException.notFound(
                        "Không tìm thấy giao dịch thanh toán."
                ));

        // 2. Khóa Checkout trước để đồng bộ với luồng hủy đơn
        Long checkoutId = paymentSnapshot.getCheckout().getId();

        CheckoutBatch checkout = checkoutBatchRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> ApiException.notFound(
                        "Không tìm thấy phiên thanh toán."
                ));

        // 3. Khóa Payment thuộc Checkout
        Payment payment = paymentRepository.findByCheckoutIdForUpdate(checkoutId)
                .stream()
                .filter(p -> p.getId().equals(paymentSnapshot.getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound(
                        "Không tìm thấy giao dịch thanh toán."
                ));

        // 4. Khóa Order thuộc Checkout
        Order order = orderRepository.findByCheckoutIdForUpdate(checkoutId)
                .orElseThrow(() -> ApiException.notFound(
                        "Không tìm thấy đơn hàng."
                ));

        // 5. Kiểm tra quyền sở hữu
        Long currentUserId = SecurityUtils.currentUserId();

        if (!checkout.getBuyer().getId().equals(currentUserId)) {
            throw ApiException.forbidden(
                    "Bạn không có quyền thanh toán đơn hàng này."
            );
        }

        // 6. Kiểm tra trạng thái
        if (payment.getStatus() != GatewayPaymentStatus.PENDING) {
            throw ApiException.badRequest(
                    "Giao dịch này không còn ở trạng thái chờ thanh toán."
            );
        }

        if (checkout.getStatus() != CheckoutBatchStatus.PENDING) {
            throw ApiException.badRequest(
                    "Phiên thanh toán không còn hợp lệ."
            );
        }

        if (checkout.getExpiresAt() == null
                || !checkout.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest(
                    "Phiên thanh toán đã hết hạn."
            );
        }

        if (order.getStatus() != OrderStatus.WAITING_PAYMENT
                || order.getPaymentStatus() != OrderPaymentStatus.PENDING) {
            throw ApiException.badRequest(
                    "Đơn hàng không còn ở trạng thái chờ thanh toán."
            );
        }

        // 7. Xử lý kết quả mô phỏng
        String action = request.getAction()
                .trim()
                .toUpperCase(Locale.ROOT);

        LocalDateTime now = LocalDateTime.now();

        if ("SUCCESS".equals(action)) {

            payment.setStatus(GatewayPaymentStatus.SUCCEEDED);
            payment.setPaidAt(now);
            payment.setProviderTransactionId(
                    "MOCK-" + payment.getPaymentCode()
            );

            checkout.setStatus(CheckoutBatchStatus.PAID);
            checkout.setCompletedAt(now);

            order.setPaymentStatus(OrderPaymentStatus.PAID);
            order.setStatus(OrderStatus.WAITING_CONFIRM);

        } else if ("FAILED".equals(action)) {

            payment.setStatus(GatewayPaymentStatus.FAILED);

            // Checkout và Order vẫn chờ thanh toán.
            // Chưa giải phóng tồn kho hoặc voucher ở bước này.

        } else {
            throw ApiException.badRequest(
                    "Action không hợp lệ. Chỉ chấp nhận SUCCESS hoặc FAILED."
            );
        }

        paymentRepository.save(payment);

        return MockPaymentResponse.from(payment);
    }

    @Transactional
    public MockPaymentResponse retryPayment(String paymentCode) {

        // 1. Tìm Payment cũ
        Payment oldPayment = paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> ApiException.notFound(
                        "Không tìm thấy giao dịch thanh toán."
                ));

        // 2. Khóa Checkout
        Long checkoutId = oldPayment.getCheckout().getId();

        CheckoutBatch checkout = checkoutBatchRepository.findByIdForUpdate(checkoutId)
                .orElseThrow(() -> ApiException.notFound(
                        "Không tìm thấy phiên thanh toán."
                ));

        // 3. Khóa các Payment thuộc Checkout
        var payments = paymentRepository.findByCheckoutIdForUpdate(checkoutId);

        Payment pendingPayment = payments.stream()
        .filter(p -> p.getStatus() == GatewayPaymentStatus.PENDING)
        .findFirst()
        .orElse(null);

        if (pendingPayment != null) {
            return MockPaymentResponse.from(pendingPayment);
        }

        Payment lockedOldPayment = payments.stream()
                .filter(p -> p.getId().equals(oldPayment.getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound(
                        "Không tìm thấy giao dịch thanh toán."
                ));

        // 4. Khóa Order
        Order order = orderRepository.findByCheckoutIdForUpdate(checkoutId)
                .orElseThrow(() -> ApiException.notFound(
                        "Không tìm thấy đơn hàng."
                ));

        // 5. Kiểm tra quyền sở hữu
        Long buyerId = SecurityUtils.currentUserId();

        if (!checkout.getBuyer().getId().equals(buyerId)) {
            throw ApiException.forbidden(
                    "Bạn không có quyền thanh toán đơn hàng này."
            );
        }

        // 6. Chỉ cho phép retry Payment FAILED
        if (lockedOldPayment.getStatus() != GatewayPaymentStatus.FAILED) {
            throw ApiException.badRequest(
                    "Chỉ có thể thanh toán lại giao dịch đã thất bại."
            );
        }

        // 7. Kiểm tra Checkout
        if (checkout.getStatus() != CheckoutBatchStatus.PENDING) {
            throw ApiException.badRequest(
                    "Phiên thanh toán không còn hợp lệ."
            );
        }

        if (checkout.getExpiresAt() == null
                || !checkout.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest(
                    "Phiên thanh toán đã hết hạn."
            );
        }

        // 8. Kiểm tra Order
        if (order.getStatus() != OrderStatus.WAITING_PAYMENT
                || order.getPaymentStatus() != OrderPaymentStatus.PENDING) {
            throw ApiException.badRequest(
                    "Đơn hàng không còn ở trạng thái chờ thanh toán."
            );
        }

        // 9. Tạo Payment attempt mới
        Payment newPayment = new Payment();

        newPayment.setPaymentCode(CodeGenerator.code("PAY"));
        newPayment.setCheckout(checkout);
        newPayment.setMethod(lockedOldPayment.getMethod());
        newPayment.setAmount(checkout.getTotalAmount());
        newPayment.setStatus(GatewayPaymentStatus.PENDING);
        newPayment.setIdempotencyKey(
                "RETRY-" + UUID.randomUUID()
        );

        Payment savedPayment = paymentRepository.save(newPayment);

        return MockPaymentResponse.from(savedPayment);
    }

}
