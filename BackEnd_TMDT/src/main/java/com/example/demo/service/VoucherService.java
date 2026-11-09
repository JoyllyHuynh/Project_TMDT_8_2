
package com.example.demo.service;

import com.example.demo.dto.voucher.VoucherValidateRequest;
import com.example.demo.dto.voucher.VoucherValidateResponse;
import com.example.demo.dto.voucher.VoucherAvailableRequest;
import com.example.demo.dto.voucher.VoucherAvailableResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.Product;
import com.example.demo.model.Voucher;
import com.example.demo.model.enums.VoucherDiscountType;
import com.example.demo.model.enums.VoucherType;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.VoucherRepository;
import com.example.demo.repository.VoucherUsageRepository;
import com.example.demo.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.model.enums.ShippingMethod;
import com.example.demo.model.Order;
import com.example.demo.model.User;
import com.example.demo.model.VoucherUsage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final ProductRepository productRepository;
    private final ShippingFeeService shippingFeeService;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<VoucherAvailableResponse> getAvailableVouchers(
            VoucherAvailableRequest request
    ) {
        Long userId = SecurityUtils.currentUserId();
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(request.getProductId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm."));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw ApiException.badRequest("Sản phẩm hiện không còn được kinh doanh.");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Giá sản phẩm không hợp lệ.");
        }
        if (request.getQuantity() == null || request.getQuantity() < 1) {
            throw ApiException.badRequest("Số lượng sản phẩm không hợp lệ.");
        }
        if (request.getQuantity() > product.getStockQuantity()) {
            throw ApiException.badRequest("Số lượng sản phẩm vượt quá tồn kho.");
        }

        BigDecimal subtotal = money(product.getPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity())));

        return voucherRepository.findAllByActiveTrueOrderByEndAtAsc().stream()
                .filter(voucher -> isEligibleForCheckout(voucher, product, subtotal, userId))
                .map(this::toAvailableResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VoucherValidateResponse validateVouchers(VoucherValidateRequest request) {

        Long userId = SecurityUtils.currentUserId();

        Product product = productRepository
                .findByIdAndDeletedAtIsNull(request.getProductId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm."));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw ApiException.badRequest("Sản phẩm hiện không còn được kinh doanh.");
        }

        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Giá sản phẩm không hợp lệ.");
        }

        if (request.getQuantity() == null || request.getQuantity() < 1) {
            throw ApiException.badRequest("Số lượng sản phẩm không hợp lệ.");
        }

        if (request.getQuantity() > product.getStockQuantity()) {
            throw ApiException.badRequest("Số lượng sản phẩm vượt quá tồn kho.");
        }

        BigDecimal subtotal = money(product.getPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity())));

        ShippingMethod shippingMethod = request.getShippingMethod();

        BigDecimal shippingFee = money(shippingFeeService.calculateShippingFee(shippingMethod));

        // Không cho phép nhập trùng một mã ở nhiều loại voucher.
        Set<String> codes = new HashSet<>();

        addCodeIfPresent(codes, request.getPlatformCode());
        addCodeIfPresent(codes, request.getSellerCode());
        addCodeIfPresent(codes, request.getShippingCode());

        BigDecimal platformDiscount = money(BigDecimal.ZERO);
        BigDecimal sellerDiscount = money(BigDecimal.ZERO);
        BigDecimal shippingDiscount = money(BigDecimal.ZERO);

        if (hasCode(request.getPlatformCode())) {
            Voucher voucher = validateVoucher(
                    request.getPlatformCode(),
                    VoucherType.PLATFORM,
                    product,
                    subtotal,
                    userId,
                    false
            );

            platformDiscount = calculateDiscount(voucher, subtotal);
        }

        BigDecimal remainingSubtotal = subtotal.subtract(platformDiscount);

        if (hasCode(request.getSellerCode())) {
            Voucher voucher = validateVoucher(
                    request.getSellerCode(),
                    VoucherType.SELLER,
                    product,
                    subtotal,
                    userId,
                    false
            );

            sellerDiscount = calculateDiscount(voucher, remainingSubtotal);
        }

        if (hasCode(request.getShippingCode())) {
            Voucher voucher = validateVoucher(
                    request.getShippingCode(),
                    VoucherType.SHIPPING,
                    product,
                    subtotal,
                    userId,
                    false
            );

            shippingDiscount = calculateDiscount(voucher, shippingFee);
        }

        BigDecimal totalDiscount = money(platformDiscount
                .add(sellerDiscount)
                .add(shippingDiscount));

        BigDecimal totalAmount = money(subtotal
                .add(shippingFee)
                .subtract(totalDiscount).max(BigDecimal.ZERO));

        VoucherValidateResponse response = new VoucherValidateResponse();

        response.setSubtotalAmount(subtotal);
        response.setShippingFee(shippingFee);
        response.setPlatformDiscount(platformDiscount);
        response.setSellerDiscount(sellerDiscount);
        response.setShippingDiscount(shippingDiscount);
        response.setTotalDiscount(totalDiscount);
        response.setTotalAmount(totalAmount);

        return response;
    }

    private boolean isEligibleForCheckout(
            Voucher voucher,
            Product product,
            BigDecimal subtotal,
            Long userId
    ) {
        try {
            validateVoucher(
                    voucher,
                    voucher.getCode(),
                    voucher.getVoucherType(),
                    product,
                    subtotal,
                    userId,
                    false
            );
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }

    private VoucherAvailableResponse toAvailableResponse(Voucher voucher) {
        return VoucherAvailableResponse.builder()
                .id(voucher.getId())
                .code(voucher.getCode())
                .name(voucher.getName())
                .description(voucher.getDescription())
                .voucherType(voucher.getVoucherType())
                .discountType(voucher.getDiscountType())
                .discountValue(voucher.getDiscountValue())
                .minOrderAmount(voucher.getMinOrderAmount())
                .maxDiscountAmount(voucher.getMaxDiscountAmount())
                .startAt(voucher.getStartAt())
                .endAt(voucher.getEndAt())
                .build();
    }

    private Voucher validateVoucher(
            String code,
            VoucherType expectedType,
            Product product,
            BigDecimal subtotal,
            Long userId,
            boolean alreadyAppliedToOrder
    ) {
        Voucher voucher = voucherRepository
                .findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> ApiException.badRequest(
                        "Mã voucher " + code + " không tồn tại."
                ));
        return validateVoucher(
                voucher, code, expectedType, product, subtotal, userId,
                alreadyAppliedToOrder
        );
    }

    private Voucher validateVoucher(
            Voucher voucher,
            String code,
            VoucherType expectedType,
            Product product,
            BigDecimal subtotal,
            Long userId,
            boolean alreadyAppliedToOrder
    ) {
        if (!Boolean.TRUE.equals(voucher.getActive())) {
            throw ApiException.badRequest("Voucher " + code + " hiện không hoạt động.");
        }

        if (voucher.getVoucherType() != expectedType) {
            throw ApiException.badRequest(
                    "Voucher " + code + " không thuộc loại voucher được chọn."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(voucher.getStartAt())) {
            throw ApiException.badRequest("Voucher " + code + " chưa bắt đầu.");
        }

        if (!now.isBefore(voucher.getEndAt())) {
            throw ApiException.badRequest("Voucher " + code + " đã hết hạn.");
        }

        int usedCount = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        if (alreadyAppliedToOrder && usedCount > 0) {
            usedCount--;
        }
        if (voucher.getUsageLimit() != null
                && usedCount >= voucher.getUsageLimit()) {
            throw ApiException.badRequest("Voucher " + code + " đã hết lượt sử dụng.");
        }

        if (voucher.getMinOrderAmount() != null
                && subtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw ApiException.badRequest(
                    "Đơn hàng chưa đạt giá trị tối thiểu để sử dụng voucher " + code + "."
            );
        }

        if (expectedType == VoucherType.SELLER) {
            if (voucher.getSeller() == null
                    || product.getSeller() == null
                    || !voucher.getSeller().getId().equals(product.getSeller().getId())) {
                throw ApiException.badRequest(
                        "Voucher " + code + " không áp dụng cho sản phẩm này."
                );
            }
        }

        Integer usageLimitPerUser = voucher.getUsageLimitPerUser();

        if (usageLimitPerUser != null && usageLimitPerUser > 0) {
            long usedByUser = voucherUsageRepository.countByVoucherIdAndUserId(
                    voucher.getId(),
                    userId
            );
            if (alreadyAppliedToOrder && usedByUser > 0) {
                usedByUser--;
            }

            if (usedByUser >= usageLimitPerUser) {
                throw ApiException.badRequest(
                        "Bạn đã sử dụng hết lượt cho voucher " + code + "."
                );
            }
        }

        return voucher;
    }

    @Transactional
    public VoucherValidateResponse applyVouchersForOrder(
            Product product,
            Integer quantity,
            ShippingMethod shippingMethod,
            String platformCode,
            String sellerCode,
            String shippingCode,
            User buyer,
            Order order
    ) {
        if (quantity == null || quantity < 1) {
            throw ApiException.badRequest("Số lượng sản phẩm không hợp lệ.");
        }

        order = orderRepository.findByIdForUpdate(order.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng."));

        BigDecimal subtotal = money(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        BigDecimal shippingFee = money(shippingFeeService.calculateShippingFee(shippingMethod));

        Set<String> codes = new HashSet<>();
        addCodeIfPresent(codes, platformCode);
        addCodeIfPresent(codes, sellerCode);
        addCodeIfPresent(codes, shippingCode);

        java.util.Map<String, Long> voucherIdsByCode = new java.util.HashMap<>();
        for (String code : codes) {
            Voucher voucher = voucherRepository.findByCodeIgnoreCase(code)
                    .orElseThrow(() -> ApiException.badRequest(
                            "Mã voucher " + code + " không tồn tại."
                    ));
            voucherIdsByCode.put(code, voucher.getId());
        }

        List<java.util.Map.Entry<String, Long>> vouchersInLockOrder =
                new ArrayList<>(voucherIdsByCode.entrySet());
        vouchersInLockOrder.sort(
                Comparator.comparing(java.util.Map.Entry::getValue)
        );

        java.util.Map<String, Voucher> lockedVouchers = new java.util.HashMap<>();
        for (var entry : vouchersInLockOrder) {
            Voucher voucher = voucherRepository.findByIdForUpdate(entry.getValue())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Mã voucher " + entry.getKey() + " không tồn tại."
                    ));
            lockedVouchers.put(entry.getKey(), voucher);
        }

        BigDecimal platformDiscount = money(BigDecimal.ZERO);
        BigDecimal sellerDiscount = money(BigDecimal.ZERO);
        BigDecimal shippingDiscount = money(BigDecimal.ZERO);
        List<VoucherUsage> usages = new ArrayList<>();

        if (hasCode(platformCode)) {
            ValidatedVoucher validatedVoucher = getAndValidateLockedVoucher(
                    lockedVouchers, platformCode, VoucherType.PLATFORM,
                    product, subtotal, buyer, order
            );
            Voucher voucher = validatedVoucher.voucher();
            platformDiscount = calculateDiscount(voucher, subtotal);
            if (!validatedVoucher.alreadyApplied()) {
                usages.add(createVoucherUsage(voucher, buyer, order, platformDiscount));
            }
        }

        BigDecimal remainingSubtotal = subtotal.subtract(platformDiscount);
        if (hasCode(sellerCode)) {
            ValidatedVoucher validatedVoucher = getAndValidateLockedVoucher(
                    lockedVouchers, sellerCode, VoucherType.SELLER,
                    product, subtotal, buyer, order
            );
            Voucher voucher = validatedVoucher.voucher();
            sellerDiscount = calculateDiscount(voucher, remainingSubtotal);
            if (!validatedVoucher.alreadyApplied()) {
                usages.add(createVoucherUsage(voucher, buyer, order, sellerDiscount));
            }
        }

        if (hasCode(shippingCode)) {
            ValidatedVoucher validatedVoucher = getAndValidateLockedVoucher(
                    lockedVouchers, shippingCode, VoucherType.SHIPPING,
                    product, subtotal, buyer, order
            );
            Voucher voucher = validatedVoucher.voucher();
            shippingDiscount = calculateDiscount(voucher, shippingFee);
            if (!validatedVoucher.alreadyApplied()) {
                usages.add(createVoucherUsage(voucher, buyer, order, shippingDiscount));
            }
        }

        BigDecimal totalDiscount = money(platformDiscount
                .add(sellerDiscount)
                .add(shippingDiscount));
        BigDecimal totalAmount = money(subtotal.add(shippingFee)
                .subtract(totalDiscount).max(BigDecimal.ZERO));

        for (VoucherUsage usage : usages) {
            voucherUsageRepository.save(usage);
            Voucher voucher = usage.getVoucher();
            int usedCount = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
            voucher.setUsedCount(usedCount + 1);
        }

        return createResponse(
                subtotal, shippingFee, platformDiscount, sellerDiscount,
                shippingDiscount, totalDiscount, totalAmount
        );
    }

    private ValidatedVoucher getAndValidateLockedVoucher(
            java.util.Map<String, Voucher> lockedVouchers,
            String code,
            VoucherType expectedType,
            Product product,
            BigDecimal subtotal,
            User buyer,
            Order order
    ) {
        Voucher voucher = lockedVouchers.get(normalizeCode(code));
        if (voucher == null) {
            throw ApiException.badRequest("Không tìm thấy voucher.");
        }

        boolean alreadyApplied = voucherUsageRepository
                .findByVoucherIdAndOrderIdForUpdate(voucher.getId(), order.getId())
                .isPresent();
        validateVoucher(
                voucher,
                code,
                expectedType,
                product,
                subtotal,
                buyer.getId(),
                alreadyApplied
        );
        return new ValidatedVoucher(voucher, alreadyApplied);
    }

    private VoucherUsage createVoucherUsage(
            Voucher voucher,
            User buyer,
            Order order,
            BigDecimal discountAmount
    ) {
        VoucherUsage usage = new VoucherUsage();
        usage.setVoucher(voucher);
        usage.setUser(buyer);
        usage.setOrder(order);
        usage.setDiscountAmount(discountAmount);
        return usage;
    }

    @Transactional
    public void releaseVouchersForOrder(Order order) {
        Order lockedOrder = orderRepository.findByIdForUpdate(order.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng."));

        List<VoucherUsage> usages = voucherUsageRepository.findByOrder(lockedOrder);
        if (usages.isEmpty()) {
            return;
        }

        java.util.Map<Long, Long> usageCounts = usages.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        usage -> usage.getVoucher().getId(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()
                ));

        for (var entry : usageCounts.entrySet()) {
            Voucher voucher = voucherRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> ApiException.notFound(
                            "Không tìm thấy Voucher cần hoàn trả."
                    ));
            int currentUsedCount = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
            voucher.setUsedCount(Math.max(0, currentUsedCount - entry.getValue().intValue()));
        }

        voucherUsageRepository.deleteAll(usages);
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal baseAmount) {

        if (baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return money(BigDecimal.ZERO);
        }

        if (voucher.getDiscountValue() == null
                || voucher.getDiscountValue().compareTo(BigDecimal.ZERO) < 0
                || (voucher.getMaxDiscountAmount() != null
                && voucher.getMaxDiscountAmount().compareTo(BigDecimal.ZERO) < 0)) {
            throw ApiException.badRequest("Giá trị giảm giá voucher không hợp lệ.");
        }

        BigDecimal discount;

        if (voucher.getDiscountType() == VoucherDiscountType.FIXED) {
            discount = voucher.getDiscountValue();
        } else if (voucher.getDiscountType() == VoucherDiscountType.PERCENTAGE) {
            discount = baseAmount
                    .multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            throw ApiException.badRequest("Loại giảm giá voucher không hợp lệ.");
        }

        if (voucher.getMaxDiscountAmount() != null) {
            discount = discount.min(voucher.getMaxDiscountAmount());
        }

        // Không giảm quá số tiền còn lại của khoản đang áp dụng.
        discount = discount.min(baseAmount);

        return money(discount);
    }

    private boolean hasCode(String code) {
        return code != null && !code.isBlank();
    }

    private void addCodeIfPresent(Set<String> codes, String code) {
        if (hasCode(code) && !codes.add(normalizeCode(code))) {
            throw ApiException.badRequest("Không được nhập trùng một mã voucher.");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private VoucherValidateResponse createResponse(
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal platformDiscount,
            BigDecimal sellerDiscount,
            BigDecimal shippingDiscount,
            BigDecimal totalDiscount,
            BigDecimal totalAmount
    ) {
        VoucherValidateResponse response = new VoucherValidateResponse();
        response.setSubtotalAmount(subtotal);
        response.setShippingFee(shippingFee);
        response.setPlatformDiscount(platformDiscount);
        response.setSellerDiscount(sellerDiscount);
        response.setShippingDiscount(shippingDiscount);
        response.setTotalDiscount(totalDiscount);
        response.setTotalAmount(totalAmount);
        return response;
    }

    private record ValidatedVoucher(Voucher voucher, boolean alreadyApplied) {
    }
}
