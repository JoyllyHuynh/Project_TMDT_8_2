package com.example.demo.service;

import com.example.demo.dto.voucher.VoucherValidateRequest;
import com.example.demo.dto.voucher.VoucherValidateResponse;
import com.example.demo.dto.voucher.VoucherAvailableRequest;
import com.example.demo.dto.voucher.VoucherAvailableResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.Order;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.model.Voucher;
import com.example.demo.model.VoucherUsage;
import com.example.demo.model.enums.ShippingMethod;
import com.example.demo.model.enums.UserRole;
import com.example.demo.model.enums.VoucherDiscountType;
import com.example.demo.model.enums.VoucherType;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.VoucherRepository;
import com.example.demo.repository.VoucherUsageRepository;
import com.example.demo.security.UserPrincipal;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoucherServiceTest {

    private static final long BUYER_ID = 5L;
    private static final long SELLER_ID = 9L;

    private VoucherRepository voucherRepository;
    private VoucherUsageRepository voucherUsageRepository;
    private ProductRepository productRepository;
    private ShippingFeeService shippingFeeService;
    private OrderRepository orderRepository;
    private VoucherService voucherService;
    private Product product;
    private User buyer;

    @BeforeEach
    void setUp() {
        voucherRepository = mock(VoucherRepository.class);
        voucherUsageRepository = mock(VoucherUsageRepository.class);
        productRepository = mock(ProductRepository.class);
        shippingFeeService = mock(ShippingFeeService.class);
        orderRepository = mock(OrderRepository.class);
        voucherService = new VoucherService(
                voucherRepository,
                voucherUsageRepository,
                productRepository,
                shippingFeeService,
                orderRepository
        );

        User seller = new User();
        seller.setId(SELLER_ID);
        seller.setRole(UserRole.USER);
        product = new Product();
        product.setId(1L);
        product.setSeller(seller);
        product.setPrice(new BigDecimal("100.00"));
        product.setStockQuantity(10);
        product.setActive(true);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(shippingFeeService.calculateShippingFee(ShippingMethod.STANDARD))
                .thenReturn(new BigDecimal("20.00"));

        buyer = new User();
        buyer.setId(BUYER_ID);
        buyer.setRole(UserRole.USER);
        UserPrincipal principal = new UserPrincipal(buyer);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                )
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewsSubtotalAndShippingWithoutPersistingUsage() {
        VoucherValidateResponse response = preview(null, null, null);

        assertMoney("100.00", response.getSubtotalAmount());
        assertMoney("20.00", response.getShippingFee());
        assertMoney("0.00", response.getTotalDiscount());
        assertMoney("120.00", response.getTotalAmount());
        verify(voucherUsageRepository, never()).save(any());
    }

    @Test
    void listsOnlyActiveVouchersEligibleForTheCurrentBuyerAndProduct() {
        Voucher platform = voucher(VoucherType.PLATFORM, VoucherDiscountType.PERCENTAGE, "10");
        platform.setId(1L);
        platform.setCode("PLATFORM-OK");
        platform.setMaxDiscountAmount(new BigDecimal("15.00"));

        Voucher seller = voucher(VoucherType.SELLER, VoucherDiscountType.FIXED, "10");
        seller.setId(2L);
        seller.setCode("SELLER-OK");
        seller.setSeller(user(SELLER_ID));

        Voucher expired = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "5");
        expired.setId(3L);
        expired.setCode("EXPIRED");
        expired.setEndAt(LocalDateTime.now().minusMinutes(1));

        Voucher minimumNotMet = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "5");
        minimumNotMet.setId(4L);
        minimumNotMet.setCode("MINIMUM-NOT-MET");
        minimumNotMet.setMinOrderAmount(new BigDecimal("101.00"));

        Voucher sellerMismatch = voucher(VoucherType.SELLER, VoucherDiscountType.FIXED, "5");
        sellerMismatch.setId(5L);
        sellerMismatch.setCode("OTHER-SELLER");
        sellerMismatch.setSeller(user(SELLER_ID + 1));

        Voucher userLimitReached = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "5");
        userLimitReached.setId(6L);
        userLimitReached.setCode("USER-LIMIT");
        userLimitReached.setUsageLimitPerUser(1);

        Voucher globalLimitReached = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "5");
        globalLimitReached.setId(7L);
        globalLimitReached.setCode("GLOBAL-LIMIT");
        globalLimitReached.setUsageLimit(1);
        globalLimitReached.setUsedCount(1);

        when(voucherRepository.findAllByActiveTrueOrderByEndAtAsc()).thenReturn(List.of(
                platform, seller, expired, minimumNotMet, sellerMismatch,
                userLimitReached, globalLimitReached
        ));
        when(voucherUsageRepository.countByVoucherIdAndUserId(6L, BUYER_ID)).thenReturn(1L);

        VoucherAvailableRequest request = new VoucherAvailableRequest();
        request.setProductId(1L);
        request.setQuantity(1);
        request.setShippingMethod(ShippingMethod.STANDARD);

        List<VoucherAvailableResponse> available = voucherService.getAvailableVouchers(request);

        assertEquals(List.of("PLATFORM-OK", "SELLER-OK"),
                available.stream().map(VoucherAvailableResponse::getCode).sorted().toList());
        assertEquals(VoucherDiscountType.PERCENTAGE, available.get(0).getDiscountType());
        assertEquals(new BigDecimal("15.00"), available.get(0).getMaxDiscountAmount());
    }

    @Test
    void previewsFixedAndPercentagePlatformDiscounts() {
        stubVoucher("PLATFORM", voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "30"));
        VoucherValidateResponse fixed = preview("PLATFORM", null, null);
        assertMoney("30.00", fixed.getPlatformDiscount());
        assertMoney("90.00", fixed.getTotalAmount());

        stubVoucher("PERCENT", voucher(VoucherType.PLATFORM, VoucherDiscountType.PERCENTAGE, "20"));
        Voucher percentage = voucher(VoucherType.PLATFORM, VoucherDiscountType.PERCENTAGE, "20");
        percentage.setMaxDiscountAmount(new BigDecimal("15.00"));
        stubVoucher("PERCENT", percentage);
        VoucherValidateResponse percent = preview("PERCENT", null, null);
        assertMoney("15.00", percent.getPlatformDiscount());
        assertMoney("105.00", percent.getTotalAmount());
    }

    @Test
    void previewsFixedAndPercentageSellerDiscountsOnlyForTheProductSeller() {
        Voucher fixedVoucher = voucher(VoucherType.SELLER, VoucherDiscountType.FIXED, "15");
        fixedVoucher.setSeller(user(SELLER_ID));
        stubVoucher("SELLER-FIXED", fixedVoucher);
        assertMoney("15.00", preview(null, "SELLER-FIXED", null).getSellerDiscount());

        Voucher percentageVoucher = voucher(VoucherType.SELLER, VoucherDiscountType.PERCENTAGE, "25");
        percentageVoucher.setSeller(user(SELLER_ID));
        stubVoucher("SELLER-PERCENT", percentageVoucher);
        assertMoney("25.00", preview(null, "SELLER-PERCENT", null).getSellerDiscount());

        percentageVoucher.setSeller(user(SELLER_ID + 1));
        assertInvalidPreview(null, "SELLER-PERCENT", null);
    }

    @Test
    void previewsFixedAndPercentageShippingDiscountsWithinShippingFee() {
        Voucher fixedVoucher = voucher(VoucherType.SHIPPING, VoucherDiscountType.FIXED, "50");
        stubVoucher("SHIP-FIXED", fixedVoucher);
        assertMoney("20.00", preview(null, null, "SHIP-FIXED").getShippingDiscount());

        stubVoucher(
                "SHIP-PERCENT",
                voucher(VoucherType.SHIPPING, VoucherDiscountType.PERCENTAGE, "12.5")
        );
        assertMoney("2.50", preview(null, null, "SHIP-PERCENT").getShippingDiscount());
    }

    @Test
    void combinesAllDiscountTypesAndCapsTheTotalAtTheOrderAmount() {
        stubVoucher("PLAT", voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "70"));
        Voucher sellerVoucher = voucher(VoucherType.SELLER, VoucherDiscountType.FIXED, "80");
        sellerVoucher.setSeller(user(SELLER_ID));
        stubVoucher("SELL", sellerVoucher);
        stubVoucher("SHIP", voucher(VoucherType.SHIPPING, VoucherDiscountType.FIXED, "80"));

        VoucherValidateResponse response = preview("PLAT", "SELL", "SHIP");

        assertMoney("70.00", response.getPlatformDiscount());
        assertMoney("30.00", response.getSellerDiscount());
        assertMoney("20.00", response.getShippingDiscount());
        assertMoney("120.00", response.getTotalDiscount());
        assertMoney("0.00", response.getTotalAmount());
    }

    @Test
    void rejectsMissingInactiveNotYetValidAndExpiredVouchers() {
        assertInvalidPreview("MISSING", null, null);

        Voucher inactive = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "10");
        inactive.setActive(false);
        stubVoucher("INACTIVE", inactive);
        assertInvalidPreview("INACTIVE", null, null);

        Voucher notStarted = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "10");
        notStarted.setStartAt(LocalDateTime.now().plusMinutes(1));
        stubVoucher("FUTURE", notStarted);
        assertInvalidPreview("FUTURE", null, null);

        Voucher expired = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "10");
        expired.setEndAt(LocalDateTime.now().minusMinutes(1));
        stubVoucher("EXPIRED", expired);
        assertInvalidPreview("EXPIRED", null, null);
    }

    @Test
    void rejectsExhaustedUsageLimitAndPerUserLimit() {
        Voucher exhausted = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "10");
        exhausted.setUsageLimit(2);
        exhausted.setUsedCount(2);
        stubVoucher("EXHAUSTED", exhausted);
        assertInvalidPreview("EXHAUSTED", null, null);

        Voucher perUser = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "10");
        perUser.setUsageLimitPerUser(1);
        stubVoucher("PER-USER", perUser);
        when(voucherUsageRepository.countByVoucherIdAndUserId(1L, BUYER_ID)).thenReturn(1L);
        assertInvalidPreview("PER-USER", null, null);
    }

    @Test
    void rejectsMinimumAmountAndWrongVoucherType() {
        Voucher minimum = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "10");
        minimum.setMinOrderAmount(new BigDecimal("101.00"));
        stubVoucher("MINIMUM", minimum);
        assertInvalidPreview("MINIMUM", null, null);

        stubVoucher("WRONG-TYPE", voucher(VoucherType.SHIPPING, VoucherDiscountType.FIXED, "10"));
        assertInvalidPreview("WRONG-TYPE", null, null);
    }

    @Test
    void rejectsDuplicateCodesRegardlessOfCase() {
        assertThrows(ApiException.class, () -> preview("same", "SAME", null));
    }

    @Test
    void roundsPercentageDiscountsAndDoesNotAllowNegativeVoucherConfiguration() {
        stubVoucher(
                "ROUND",
                voucher(VoucherType.PLATFORM, VoucherDiscountType.PERCENTAGE, "12.345")
        );
        assertMoney("12.35", preview("ROUND", null, null).getPlatformDiscount());

        stubVoucher(
                "NEGATIVE",
                voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "-1")
        );
        assertInvalidPreview("NEGATIVE", null, null);
    }

    @Test
    void appliesVoucherAndIncrementsUsageOnlyOnceForTheSameOrder() {
        Voucher platform = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "10");
        platform.setId(1L);
        platform.setUsageLimit(1);
        platform.setUsageLimitPerUser(1);
        stubLockedVoucher("PLATFORM", platform);
        stubVoucher("PLATFORM", platform);
        VoucherValidateResponse preview = preview("PLATFORM", null, null);
        Order order = new Order();
        order.setId(40L);

        VoucherValidateResponse first = apply("PLATFORM", order);
        assertMoney("10.00", first.getPlatformDiscount());
        assertMoney(preview.getTotalAmount().toPlainString(), first.getTotalAmount());
        verify(voucherUsageRepository).save(any());
        assertEquals(1, platform.getUsedCount());

        when(voucherUsageRepository.findByVoucherIdAndOrderIdForUpdate(1L, 40L))
                .thenReturn(Optional.of(mock(VoucherUsage.class)));
        when(voucherUsageRepository.countByVoucherIdAndUserId(1L, BUYER_ID)).thenReturn(1L);
        VoucherValidateResponse repeated = apply("PLATFORM", order);

        assertMoney("10.00", repeated.getPlatformDiscount());
        verify(voucherUsageRepository).save(any());
        assertEquals(1, platform.getUsedCount());
    }

    @Test
    void locksMultipleVouchersInAscendingIdOrder() {
        Voucher platform = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "10");
        platform.setId(2L);
        Voucher shipping = voucher(VoucherType.SHIPPING, VoucherDiscountType.FIXED, "1");
        shipping.setId(1L);
        stubLockedVoucher("PLATFORM", platform);
        stubLockedVoucher("SHIPPING", shipping);
        Order order = new Order();
        order.setId(41L);
        when(orderRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(order));

        voucherService.applyVouchersForOrder(
                product, 1, ShippingMethod.STANDARD,
                "PLATFORM", null, "SHIPPING", buyer, order
        );

        var calls = inOrder(voucherRepository);
        calls.verify(voucherRepository).findByIdForUpdate(1L);
        calls.verify(voucherRepository).findByIdForUpdate(2L);
    }

    @Test
    void repositoriesDeclarePessimisticLocksForVoucherAndUsageCriticalSections()
            throws NoSuchMethodException {
        assertEquals(
                LockModeType.PESSIMISTIC_WRITE,
                VoucherRepository.class.getMethod("findByIdForUpdate", Long.class)
                        .getAnnotation(Lock.class).value()
        );
        assertEquals(
                LockModeType.PESSIMISTIC_WRITE,
                OrderRepository.class.getMethod("findByIdForUpdate", Long.class)
                        .getAnnotation(Lock.class).value()
        );
        assertEquals(
                LockModeType.PESSIMISTIC_WRITE,
                VoucherUsageRepository.class.getMethod(
                        "findByVoucherIdAndOrderIdForUpdate", Long.class, Long.class
                ).getAnnotation(Lock.class).value()
        );
    }

    @Test
    void releaseLocksOrderBeforeReadingAndReturningVoucherUsageOnlyOnce() {
        Order order = new Order();
        order.setId(40L);
        Voucher voucher = voucher(VoucherType.PLATFORM, VoucherDiscountType.FIXED, "10");
        voucher.setId(1L);
        voucher.setUsedCount(1);
        VoucherUsage usage = new VoucherUsage();
        usage.setOrder(order);
        usage.setVoucher(voucher);
        when(orderRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(order));
        AtomicBoolean firstLookup = new AtomicBoolean(true);
        when(voucherUsageRepository.findByOrder(order)).thenAnswer(
                invocation -> firstLookup.getAndSet(false)
                        ? java.util.List.of(usage)
                        : java.util.List.of()
        );
        when(voucherRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(voucher));

        voucherService.releaseVouchersForOrder(order);
        voucherService.releaseVouchersForOrder(order);

        verify(orderRepository, org.mockito.Mockito.times(2)).findByIdForUpdate(40L);
        verify(voucherRepository).findByIdForUpdate(1L);
        verify(voucherUsageRepository).deleteAll(java.util.List.of(usage));
        assertEquals(0, voucher.getUsedCount());
    }

    private VoucherValidateResponse preview(String platform, String seller, String shipping) {
        VoucherValidateRequest request = new VoucherValidateRequest();
        request.setProductId(1L);
        request.setQuantity(1);
        request.setShippingMethod(ShippingMethod.STANDARD);
        request.setPlatformCode(platform);
        request.setSellerCode(seller);
        request.setShippingCode(shipping);  
        return voucherService.validateVouchers(request);
    }

    private VoucherValidateResponse apply(String platformCode, Order order) {
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        return voucherService.applyVouchersForOrder(
                product, 1, ShippingMethod.STANDARD,
                platformCode, null, null, buyer, order
        );
    }

    private void assertInvalidPreview(String platform, String seller, String shipping) {
        assertThrows(ApiException.class, () -> preview(platform, seller, shipping));
    }

    private void stubVoucher(String code, Voucher voucher) {
        when(voucherRepository.findByCodeIgnoreCase(code)).thenReturn(Optional.of(voucher));
    }

    private void stubLockedVoucher(String code, Voucher voucher) {
        when(voucherRepository.findByCodeIgnoreCase(code)).thenReturn(Optional.of(voucher));
        when(voucherRepository.findByIdForUpdate(voucher.getId())).thenReturn(Optional.of(voucher));
    }

    private Voucher voucher(VoucherType type, VoucherDiscountType discountType, String value) {
        Voucher voucher = new Voucher();
        voucher.setId(1L);
        voucher.setCode("TEST");
        voucher.setName("Test");
        voucher.setVoucherType(type);
        voucher.setDiscountType(discountType);
        voucher.setDiscountValue(new BigDecimal(value));
        voucher.setMinOrderAmount(BigDecimal.ZERO);
        voucher.setUsageLimitPerUser(null);
        voucher.setUsedCount(0);
        voucher.setActive(true);
        voucher.setStartAt(LocalDateTime.now().minusDays(1));
        voucher.setEndAt(LocalDateTime.now().plusDays(1));
        return voucher;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        user.setRole(UserRole.USER);
        return user;
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
        assertEquals(2, actual.scale());
    }
}
