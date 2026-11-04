package com.example.demo.service;

import com.example.demo.dto.order.CheckoutRequest;
import com.example.demo.exception.ApiException;
import com.example.demo.model.CheckoutBatch;
import com.example.demo.model.Order;
import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.CheckoutBatchStatus;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.CheckoutBatchRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VoucherRepository;
import com.example.demo.repository.VoucherUsageRepository;
import com.example.demo.security.UserPrincipal;
import com.example.demo.util.RequestFingerprintUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderVoucherIdempotencyTest {

    private static final long BUYER_ID = 5L;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private CheckoutBatchRepository checkoutBatchRepository;
    private VoucherService voucherService;
    private OrderService orderService;
    private CheckoutRequest request;
    private User buyer;
    private Order existingOrder;
    private CheckoutBatch checkout;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        checkoutBatchRepository = mock(CheckoutBatchRepository.class);
        voucherService = mock(VoucherService.class);

        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                productRepository,
                userRepository,
                mock(ProductImageRepository.class),
                checkoutBatchRepository,
                mock(VoucherRepository.class),
                voucherService,
                mock(VoucherUsageRepository.class),
                mock(ShippingFeeService.class)
        );

        buyer = new User();
        buyer.setId(BUYER_ID);
        buyer.setEmail("buyer@example.com");
        buyer.setRole(UserRole.USER);
        buyer.setAccountStatus(AccountStatus.ACTIVE);
        UserPrincipal principal = new UserPrincipal(buyer);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                )
        );

        request = new CheckoutRequest();
        request.setProductId(12L);
        request.setQuantity(2);
        request.setRecipientName("Buyer");
        request.setRecipientPhone("0123456789");
        request.setShippingAddress("Address");
        request.setIdempotencyKey("request-123");
        request.setPlatformCode("PLATFORM-10");

        checkout = new CheckoutBatch();
        checkout.setIdempotencyKey(request.getIdempotencyKey());
        checkout.setBuyer(buyer);
        checkout.setRequestFingerprint(RequestFingerprintUtil.generate(request));
        checkout.setStatus(CheckoutBatchStatus.PENDING);
        checkout.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        User seller = new User();
        seller.setId(8L);
        existingOrder = new Order();
        existingOrder.setId(31L);
        existingOrder.setCheckout(checkout);
        existingOrder.setBuyer(buyer);
        existingOrder.setSeller(seller);
        existingOrder.setStatus(OrderStatus.WAITING_PAYMENT);
        existingOrder.setPaymentStatus(OrderPaymentStatus.PENDING);

        when(userRepository.findByIdForUpdate(BUYER_ID)).thenReturn(Optional.of(buyer));
        when(checkoutBatchRepository.findByIdempotencyKeyForUpdate("request-123"))
                .thenReturn(Optional.of(checkout));
        when(orderRepository.findFirstByCheckoutOrderByCreatedAtDesc(checkout))
                .thenReturn(Optional.of(existingOrder));
        when(orderItemRepository.findByOrder(existingOrder)).thenReturn(List.of());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retriesTheSameVoucherRequestByReturningTheExistingOrder() {
        var response = orderService.buyNow(request);

        assertEquals(31L, response.getId());
        verify(voucherService, never()).applyVouchersForOrder(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(productRepository, never()).findByIdForUpdate(12L);
    }

    @Test
    void rejectsReuseOfAnIdempotencyKeyWhenVoucherCodeChanges() {
        request.setPlatformCode("PLATFORM-20");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> orderService.buyNow(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(voucherService, never()).applyVouchersForOrder(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void fingerprintIncludesEveryVoucherCode() {
        String initial = RequestFingerprintUtil.generate(request);

        request.setPlatformCode("PLATFORM-20");
        assertNotEquals(initial, RequestFingerprintUtil.generate(request));

        request.setPlatformCode("PLATFORM-10");
        request.setSellerCode("SELLER-10");
        assertNotEquals(initial, RequestFingerprintUtil.generate(request));

        request.setSellerCode(null);
        request.setShippingCode("SHIP-10");
        assertNotEquals(initial, RequestFingerprintUtil.generate(request));
    }
}
