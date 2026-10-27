package com.example.demo.service;

import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceChatbotTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chatbotOrderSummaryIsBoundedToCurrentBuyerAndOmitsShippingDetails() {
        User buyer = new User();
        buyer.setId(27L);
        buyer.setEmail("buyer@example.com");
        buyer.setRole(UserRole.USER);
        buyer.setAccountStatus(AccountStatus.ACTIVE);
        UserPrincipal principal = new UserPrincipal(buyer);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        Order order = mock(Order.class);
        when(order.getOrderCode()).thenReturn("CW-ORDER-27");
        when(order.getStatus()).thenReturn(OrderStatus.WAITING_CONFIRM);
        when(order.getPaymentStatus()).thenReturn(OrderPaymentStatus.PENDING);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("50000"));
        when(order.getCreatedAt()).thenReturn(LocalDateTime.of(2025, 1, 1, 12, 0));
        when(orderRepository.findTop5ByBuyerIdOrderByCreatedAtDesc(27L)).thenReturn(List.of(order));

        OrderItem item = mock(OrderItem.class);
        when(item.getProductTitle()).thenReturn("Naruto - Tập 1");
        when(item.getQuantity()).thenReturn(1);
        when(item.getUnitPrice()).thenReturn(new BigDecimal("50000"));
        when(orderItemRepository.findTop5ByOrderOrderByIdAsc(order)).thenReturn(List.of(item));

        OrderService service = new OrderService(
                orderRepository,
                orderItemRepository,
                mock(ProductRepository.class),
                mock(UserRepository.class),
                mock(ProductImageRepository.class)
        );
        var summaries = service.getChatbotOrderSummaries();

        assertEquals(1, summaries.size());
        assertEquals("CW-ORDER-27", summaries.get(0).orderCode());
        assertEquals("Naruto - Tập 1", summaries.get(0).items().get(0).title());
        verify(orderRepository).findTop5ByBuyerIdOrderByCreatedAtDesc(27L);
    }
}
