package com.example.demo.service;

import com.example.demo.exception.ApiException;
import com.example.demo.model.CheckoutBatch;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.CheckoutBatchStatus;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.*;
import com.example.demo.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderCancellationServiceTest {

    private static final Long ORDER_ID = 40L;
    private static final Long CHECKOUT_ID = 70L;
    private static final Long PRODUCT_ID = 90L;
    private static final Long BUYER_ID = 10L;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buyerCanCancelPackingOrderAndStockIsRestoredOnlyOnce() {
        User buyer = user(BUYER_ID);
        authenticate(buyer);
        Order order = order(buyer, OrderStatus.PACKING);
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setStockQuantity(3);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(2);

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        CheckoutBatchRepository checkoutBatchRepository = mock(CheckoutBatchRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        OrderStatusHistoryRepository historyRepository = mock(OrderStatusHistoryRepository.class);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.findByCheckoutIdForUpdate(CHECKOUT_ID)).thenReturn(Optional.of(order));
        when(checkoutBatchRepository.findByIdForUpdate(CHECKOUT_ID))
                .thenReturn(Optional.of(order.getCheckout()));
        when(orderItemRepository.findByOrder(order)).thenReturn(List.of(item));
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        OrderCancellationService service = service(
                orderRepository,
                orderItemRepository,
                productRepository,
                checkoutBatchRepository,
                historyRepository,
                notificationRepository
        );

        service.cancelOrder(ORDER_ID);
        service.cancelOrder(ORDER_ID);

        assertEquals(5, product.getStockQuantity());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(CheckoutBatchStatus.CANCELLED, order.getCheckout().getStatus());
        verify(productRepository, times(1)).findByIdForUpdate(PRODUCT_ID);
        verify(historyRepository, times(1)).save(any());
        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    void buyerCannotCancelAnotherBuyersOrder() {
        User currentBuyer = user(BUYER_ID);
        authenticate(currentBuyer);
        Order order = order(user(BUYER_ID + 1), OrderStatus.WAITING_PAYMENT);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.findByCheckoutIdForUpdate(CHECKOUT_ID)).thenReturn(Optional.of(order));

        OrderCancellationService service = service(
                orderRepository,
                mock(OrderItemRepository.class),
                productRepository,
                checkoutRepository(order),
                mock(OrderStatusHistoryRepository.class),
                mock(NotificationRepository.class)
        );

        assertThrows(ApiException.class, () -> service.cancelOrder(ORDER_ID));
        verifyNoInteractions(productRepository);
    }

    @Test
    void buyerCannotCancelOrderAfterItIsShipped() {
        User buyer = user(BUYER_ID);
        authenticate(buyer);
        Order order = order(buyer, OrderStatus.SHIPPING);
        OrderRepository orderRepository = mock(OrderRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.findByCheckoutIdForUpdate(CHECKOUT_ID)).thenReturn(Optional.of(order));

        OrderCancellationService service = service(
                orderRepository,
                mock(OrderItemRepository.class),
                productRepository,
                checkoutRepository(order),
                mock(OrderStatusHistoryRepository.class),
                mock(NotificationRepository.class)
        );

        assertThrows(ApiException.class, () -> service.cancelOrder(ORDER_ID));
        verifyNoInteractions(productRepository);
    }

    private OrderCancellationService service(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            CheckoutBatchRepository checkoutBatchRepository,
            OrderStatusHistoryRepository historyRepository,
            NotificationRepository notificationRepository
    ) {
        return new OrderCancellationService(
                orderRepository,
                orderItemRepository,
                productRepository,
                checkoutBatchRepository,
                mock(VoucherService.class),
                historyRepository,
                mock(RefundRepository.class),
                mock(PaymentAllocationRepository.class),
                notificationRepository
        );
    }

    private CheckoutBatchRepository checkoutRepository(Order order) {
        CheckoutBatchRepository repository = mock(CheckoutBatchRepository.class);
        when(repository.findByIdForUpdate(CHECKOUT_ID)).thenReturn(Optional.of(order.getCheckout()));
        return repository;
    }

    private Order order(User buyer, OrderStatus status) {
        CheckoutBatch checkout = new CheckoutBatch();
        checkout.setId(CHECKOUT_ID);
        checkout.setStatus(CheckoutBatchStatus.PENDING);

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderCode("ORD-" + ORDER_ID);
        order.setCheckout(checkout);
        order.setBuyer(buyer);
        order.setSeller(user(BUYER_ID + 2));
        order.setStatus(status);
        order.setPaymentStatus(OrderPaymentStatus.PENDING);
        return order;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setFullName("User " + id);
        user.setRole(UserRole.USER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        return user;
    }

    private void authenticate(User buyer) {
        UserPrincipal principal = new UserPrincipal(buyer);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                )
        );
    }
}
