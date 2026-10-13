package com.example.demo.service;

import com.example.demo.dto.order.CheckoutRequest;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.*;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.repository.*;
import com.example.demo.security.SecurityUtils;
import com.example.demo.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductImageRepository productImageRepository;

    @Transactional
    public OrderResponse buyNow(CheckoutRequest request) {
        UserPrincipal principal = SecurityUtils.currentUser();
        User buyer = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản người mua."));

        Product product = productRepository.findByIdAndDeletedAtIsNull(request.getProductId())
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .orElseThrow(() -> ApiException.notFound("Sản phẩm không tồn tại hoặc đã bị ẩn."));

        // 1. Kiểm tra tồn kho
        if (product.getStockQuantity() < request.getQuantity()) {
            throw ApiException.badRequest("Số lượng sản phẩm trong kho không đủ.");
        }

        // 2. Trừ tồn kho
        product.setStockQuantity(product.getStockQuantity() - request.getQuantity());
        productRepository.save(product);

        // 3. Tính tiền
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        BigDecimal shippingFee = request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(shippingFee);

        // 4. Khởi tạo Đơn hàng (Order)
        Order order = new Order();
        order.setOrderCode("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setBuyer(buyer);
        order.setSeller(product.getSeller());
        order.setSubtotalAmount(subtotal);
        order.setShippingFee(shippingFee);
        order.setTotalAmount(total);
        order.setRecipientName(request.getRecipientName());
        order.setRecipientPhone(request.getRecipientPhone());
        order.setShippingAddress(request.getShippingAddress());
        order.setBuyerNote(request.getBuyerNote());
        order.setStatus(OrderStatus.WAITING_CONFIRM);
        order.setPaymentStatus(OrderPaymentStatus.PENDING);
        Order savedOrder = orderRepository.save(order);

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

        return OrderResponse.from(savedOrder, List.of(savedItem));
    }
}