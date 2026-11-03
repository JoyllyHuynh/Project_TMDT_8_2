package com.example.demo.service;

import com.example.demo.dto.order.UpdateOrderStatusRequest;
import com.example.demo.model.Order;
import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.AuthSessionRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.repository.PaymentAllocationRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.RefundRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderCancellationVoucherTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administrativeCancellationReleasesVoucherUsageWithinTheOrderTransaction() {
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@example.com");
        admin.setFullName("Admin");
        admin.setRole(UserRole.ADMIN);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        UserPrincipal principal = new UserPrincipal(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                )
        );

        User buyer = new User();
        buyer.setId(2L);
        User seller = new User();
        seller.setId(3L);
        Order order = new Order();
        order.setId(10L);
        order.setBuyer(buyer);
        order.setSeller(seller);
        order.setStatus(OrderStatus.WAITING_PAYMENT);
        order.setPaymentStatus(OrderPaymentStatus.PENDING);

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        VoucherService voucherService = mock(VoucherService.class);
        OrderStatusHistoryRepository historyRepository = mock(OrderStatusHistoryRepository.class);
        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderItemRepository.findByOrder(order)).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        AdminService adminService = new AdminService(
                userRepository,
                mock(ProductRepository.class),
                mock(ProductImageRepository.class),
                orderRepository,
                orderItemRepository,
                historyRepository,
                mock(AuthSessionRepository.class),
                mock(RefundRepository.class),
                mock(PaymentAllocationRepository.class),
                mock(PasswordEncoder.class),
                voucherService
        );

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);
        request.setNote("Cancelled by admin");

        adminService.updateOrderStatus(10L, request);

        verify(voucherService).releaseVouchersForOrder(order);
        verify(orderRepository).findByIdForUpdate(10L);
    }
}
