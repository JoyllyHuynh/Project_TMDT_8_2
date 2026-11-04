package com.example.demo.service;

import com.example.demo.model.CheckoutBatch;
import com.example.demo.model.Order;
import com.example.demo.model.enums.CheckoutBatchStatus;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.repository.CheckoutBatchRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckoutBatchVoucherExpiryTest {

    @Test
    void expiryLocksOrderAndReleasesVoucherUsageForPendingOrder() {
        CheckoutBatch checkout = new CheckoutBatch();
        checkout.setId(21L);
        checkout.setStatus(CheckoutBatchStatus.PENDING);
        checkout.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        Order order = new Order();
        order.setId(31L);
        order.setCheckout(checkout);
        order.setStatus(OrderStatus.WAITING_PAYMENT);
        order.setPaymentStatus(OrderPaymentStatus.PENDING);
        order.setStockReleasedAt(LocalDateTime.now());

        CheckoutBatchRepository checkoutRepository = mock(CheckoutBatchRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        VoucherService voucherService = mock(VoucherService.class);
        when(checkoutRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(checkout));
        when(orderRepository.findByCheckoutIdForUpdate(21L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrder(order)).thenReturn(List.of());

        CheckoutBatchExpiryService service = new CheckoutBatchExpiryService(
                checkoutRepository,
                orderRepository,
                orderItemRepository,
                mock(ProductRepository.class),
                voucherService
        );

        service.expireCheckoutBatch(21L);

        verify(voucherService).releaseVouchersForOrder(order);
        verify(orderRepository).findByCheckoutIdForUpdate(21L);
    }
}
