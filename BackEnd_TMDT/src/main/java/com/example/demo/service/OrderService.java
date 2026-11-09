package com.example.demo.service;

import com.example.demo.dto.order.CheckoutRequest;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.dto.voucher.VoucherValidateResponse;
import com.example.demo.dto.chatbot.ChatbotOrderSummary;
import com.example.demo.exception.ApiException;
import com.example.demo.model.*;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.repository.*;
import com.example.demo.security.SecurityUtils;
import com.example.demo.security.UserPrincipal;
import com.example.demo.util.CodeGenerator;
import com.example.demo.util.RequestFingerprintUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.model.enums.CheckoutBatchStatus;
import java.time.LocalDateTime;
import com.example.demo.model.enums.GatewayPaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductImageRepository productImageRepository;
    private final PaymentRepository paymentRepository;
    private final CheckoutBatchRepository checkoutBatchRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherService voucherService;
    private final VoucherUsageRepository voucherUsageRepository;
    private final ShippingFeeService shippingFeeService;

    @Transactional
    public OrderResponse buyNow(CheckoutRequest request) {
        UserPrincipal principal = SecurityUtils.currentUser();
        User buyer = userRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản người mua."));

        // 1. Kiểm tra idempotency key và request fingerprint
        String idempotencyKey = request.getIdempotencyKey().trim();

        String requestFingerprint = RequestFingerprintUtil.generate(request);

        var existingCheckout = checkoutBatchRepository
                .findByIdempotencyKeyForUpdate(idempotencyKey);

        if (existingCheckout.isPresent()) {
            CheckoutBatch checkout = existingCheckout.get();

            if (!checkout.getBuyer().getId().equals(buyer.getId())) {
                throw ApiException.badRequest("Idempotency key không hợp lệ.");
            }

            if (!requestFingerprint.equals(checkout.getRequestFingerprint())) {
                throw ApiException.conflict(
                        "Idempotency key đã được sử dụng cho một yêu cầu khác."
                );
            }

            Order existingOrder = orderRepository
                    .findFirstByCheckoutOrderByCreatedAtDesc(checkout)
                    .orElseThrow(() -> ApiException.badRequest(
                            "Checkout đã tồn tại nhưng không tìm thấy đơn hàng."
                    ));

            // Checkout đã thanh toán: trả về kết quả cũ.
            if (checkout.getStatus() == CheckoutBatchStatus.PAID) {
                List<OrderItem> existingItems =
                        orderItemRepository.findByOrder(existingOrder);

                String paymentCode = getLatestPaymentCode(checkout);

                return OrderResponse.from(
                        existingOrder,
                        existingItems,
                        paymentCode
                );
            }

            // Checkout đang chờ thanh toán và chưa hết hạn: trả về đơn cũ.
            if (checkout.getStatus() == CheckoutBatchStatus.PENDING
                        && checkout.getExpiresAt().isAfter(LocalDateTime.now())) {

                List<OrderItem> existingItems =
                        orderItemRepository.findByOrder(existingOrder);

                String paymentCode = getLatestPaymentCode(checkout);

                return OrderResponse.from(
                        existingOrder,
                        existingItems,
                        paymentCode
                );
                }

            throw ApiException.badRequest(
                    "Checkout đã hết hạn hoặc đã bị hủy. Vui lòng tạo yêu cầu mới."
            );
        }

        Product product = productRepository.findByIdForUpdate(request.getProductId())
            .filter(p -> p.getDeletedAt() == null)
            .filter(p -> Boolean.TRUE.equals(p.getActive()))
            .orElseThrow(() -> ApiException.notFound(
                    "Sản phẩm không tồn tại hoặc đã bị ẩn."
        ));
        // kiem tra sp phai do nguoi do ban k
        if(product.getSeller().getId().equals(buyer.getId())){
            throw ApiException.badRequest("Bạn không thể tự mua sản phẩm do chính mình đăng bán.");
        }

        // 2. Kiểm tra tồn kho
        if (product.getStockQuantity() < request.getQuantity()) {
            throw ApiException.badRequest("Số lượng sản phẩm trong kho không đủ.");
        }

        // 3. Tính tiền
        BigDecimal subtotal = product.getPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()));

        BigDecimal shippingFee = shippingFeeService
                .calculateShippingFee(request.getShippingMethod());

        BigDecimal total = subtotal.add(shippingFee);

        // 4. Tạo CheckoutBatch
        CheckoutBatch checkout = new CheckoutBatch();
        checkout.setCheckoutCode(CodeGenerator.code("CHECKOUT"));
        checkout.setBuyer(buyer);
        checkout.setIdempotencyKey(idempotencyKey);
        checkout.setRequestFingerprint(requestFingerprint);
        checkout.setTotalAmount(total);
        checkout.setStatus(CheckoutBatchStatus.PENDING);
        checkout.setExpiresAt(LocalDateTime.now().plusMinutes(15));

        CheckoutBatch savedCheckout = checkoutBatchRepository.save(checkout);

        // 5. Trừ tồn kho
        product.setStockQuantity(product.getStockQuantity() - request.getQuantity());
        productRepository.save(product);

        // 6. Tạo Order và liên kết CheckoutBatch
        Order order = new Order();
        order.setOrderCode(CodeGenerator.code("ORD"));
        order.setCheckout(savedCheckout);
        order.setBuyer(buyer);
        order.setSeller(product.getSeller());
        order.setSubtotalAmount(subtotal);
        order.setShippingFee(shippingFee);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(total);
        order.setRecipientName(request.getRecipientName());
        order.setRecipientPhone(request.getRecipientPhone());
        order.setShippingAddress(request.getShippingAddress());
        order.setBuyerNote(request.getBuyerNote());
        order.setStatus(OrderStatus.WAITING_PAYMENT);
        order.setPaymentStatus(OrderPaymentStatus.PENDING);

        Order savedOrder = orderRepository.save(order);

        // 7. Áp dụng voucher và ghi nhận lượt sử dụng
        VoucherValidateResponse voucherResult =
        voucherService.applyVouchersForOrder(
                product,
                request.getQuantity(),
                request.getShippingMethod(),
                request.getPlatformCode(),
                request.getSellerCode(),
                request.getShippingCode(),
                buyer,
                savedOrder
        );

        // 8. Cập nhật số tiền cuối cùng của Order
        savedOrder.setDiscountAmount(voucherResult.getTotalDiscount());
        savedOrder.setTotalAmount(voucherResult.getTotalAmount());

        // Đồng bộ tổng tiền CheckoutBatch
        savedCheckout.setTotalAmount(voucherResult.getTotalAmount());

        // 9. Tạo Payment cho CheckoutBatch
        Payment payment = new Payment();

        payment.setPaymentCode(CodeGenerator.code("PAY"));
        payment.setCheckout(savedCheckout);
        payment.setMethod(request.getPaymentMethod());
        payment.setAmount(voucherResult.getTotalAmount());
        payment.setStatus(GatewayPaymentStatus.PENDING);
        payment.setIdempotencyKey(
                "PAY-" + savedCheckout.getCheckoutCode()
        );

        paymentRepository.save(payment);

        // 5. Khởi tạo Chi tiết đơn hàng (OrderItem)
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(savedOrder);
        orderItem.setProduct(product);
        orderItem.setProductTitle(product.getTitle());

        List<ProductImage> images = productImageRepository.findByProductOrderByDisplayOrderAsc(product);
        String primaryImg = images.isEmpty() ? "" : images.get(0).getImageUrl();
        orderItem.setImageUrl(primaryImg);

        orderItem.setConditionPercent(product.getConditionPercent());
        orderItem.setUnitPrice(product.getPrice());
        orderItem.setQuantity(request.getQuantity());
        OrderItem savedItem = orderItemRepository.save(orderItem);

        return OrderResponse.from(savedOrder, List.of(savedItem), payment.getPaymentCode());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String statusFilter, String keyword) {
        UserPrincipal principal = SecurityUtils.currentUser();

        // Lấy tất cả đơn hàng của buyer theo buyerId
        List<Order> orders = orderRepository.findByBuyerIdOrderByCreatedAtDesc(principal.getId());

        return orders.stream()
                .filter(order -> {
                    if (statusFilter == null || statusFilter.isBlank() || statusFilter.equalsIgnoreCase("ALL")) return true;
                    return order.getStatus().name().equalsIgnoreCase(statusFilter);
                })
                .filter(order -> {
                    if (keyword == null || keyword.isBlank()) return true;
                    String kw = keyword.toLowerCase();
                    return order.getOrderCode().toLowerCase().contains(kw)
                            || (order.getSeller() != null && order.getSeller().getFullName() != null && order.getSeller().getFullName().toLowerCase().contains(kw));
                })
                .map(order -> {
                        List<OrderItem> items = orderItemRepository.findByOrder(order);

                        String paymentCode = getLatestPaymentCodeOrNull(order.getCheckout());

                        return OrderResponse.from(order, items, paymentCode);
                })
                .collect(Collectors.toList());
    }

    private String getLatestPaymentCode(CheckoutBatch checkout) {
        return paymentRepository
                .findFirstByCheckoutOrderByCreatedAtDesc(checkout)
                .map(Payment::getPaymentCode)
                .orElseThrow(() -> ApiException.notFound(
                        "Không tìm thấy giao dịch thanh toán."
                ));
    }

    private String getLatestPaymentCodeOrNull(CheckoutBatch checkout) {
        return paymentRepository
                .findFirstByCheckoutOrderByCreatedAtDesc(checkout)
                .map(Payment::getPaymentCode)
                .orElse(null);
        }

    @Transactional(readOnly = true)
    public List<ChatbotOrderSummary> getChatbotOrderSummaries() {
        UserPrincipal principal = SecurityUtils.currentUser();
        return orderRepository.findTop5ByBuyerIdOrderByCreatedAtDesc(principal.getId())
                .stream()
                .map(order -> new ChatbotOrderSummary(
                        order.getOrderCode(),
                        order.getStatus().name(),
                        order.getPaymentStatus().name(),
                        order.getTotalAmount(),
                        order.getCreatedAt(),
                        orderItemRepository.findTop5ByOrderOrderByIdAsc(order).stream()
                                .map(item -> new ChatbotOrderSummary.Item(
                                        item.getProductTitle(),
                                        item.getQuantity(),
                                        item.getUnitPrice()
                                ))
                                .toList()
                ))
                .toList();
    }
}